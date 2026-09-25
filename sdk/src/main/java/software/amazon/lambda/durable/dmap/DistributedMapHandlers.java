// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.dmap;

import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import software.amazon.lambda.durable.DurableConfig;
import software.amazon.lambda.durable.DurableContext;
import software.amazon.lambda.durable.TypeToken;
import software.amazon.lambda.durable.config.CompletionConfig;
import software.amazon.lambda.durable.config.DistributedMapProcessor.ResponseMode;
import software.amazon.lambda.durable.config.MapConfig;
import software.amazon.lambda.durable.execution.DurableExecutor;
import software.amazon.lambda.durable.model.DurableExecutionInput;
import software.amazon.lambda.durable.model.DurableExecutionOutput;
import software.amazon.lambda.durable.model.MapResult;
import software.amazon.lambda.durable.serde.JacksonSerDes;
import software.amazon.lambda.durable.serde.SerDes;

/** Authoring helpers that wrap a customer function into a distributed map processor or reader Lambda handler. */
public final class DistributedMapHandlers {
    private static final SerDes JSON = new JacksonSerDes();
    private static final TypeToken<String> OUTPUT_TYPE = TypeToken.get(String.class);
    private static final int MIN_CONCURRENCY = 1;
    private static final int DEFAULT_CONCURRENCY = 1;
    private static final TypeToken<ProcessorEvent> EVENT_TYPE = TypeToken.get(ProcessorEvent.class);
    private static final int READER_STATE_LIMIT = 32 * 1024;
    private static final String ITEMS_OP_NAME = "distributed-map-items";

    private DistributedMapHandlers() {}

    private static void validateItemReport(ResponseMode report) {
        if (report != ResponseMode.ITEM_RESULTS && report != ResponseMode.ITEM_FAILURES) {
            throw new IllegalArgumentException(
                    "item handler report mode must be ITEM_RESULTS or ITEM_FAILURES, got: " + report);
        }
    }

    /** Wraps a per-item function as a processor handler, running one item at a time. */
    public static <I, O> RequestHandler<ProcessorEvent, ProcessorResponse> createDistributedMapItemHandler(
            Function<I, O> func, TypeToken<I> itemType, SerDes itemSerDes, SerDes resultSerDes, ResponseMode report) {
        return createDistributedMapItemHandler(func, itemType, itemSerDes, resultSerDes, report, DEFAULT_CONCURRENCY);
    }

    /** Wraps a per-item function as a processor handler. Use ITEM_RESULTS or ITEM_FAILURES. */
    public static <I, O> RequestHandler<ProcessorEvent, ProcessorResponse> createDistributedMapItemHandler(
            Function<I, O> func,
            TypeToken<I> itemType,
            SerDes itemSerDes,
            SerDes resultSerDes,
            ResponseMode report,
            int concurrency) {
        validateItemReport(report);
        if (concurrency < MIN_CONCURRENCY) {
            throw new IllegalArgumentException(
                    "concurrency must be at least " + MIN_CONCURRENCY + ", got: " + concurrency);
        }
        var inSerdes = itemSerDes != null ? itemSerDes : JSON;
        var outSerdes = resultSerDes != null ? resultSerDes : JSON;
        return (event, context) -> {
            var records = event.records();
            var outputs = new String[records.size()];
            var errors = new Throwable[records.size()];
            var pool = Executors.newFixedThreadPool(concurrency);
            try {
                var futures = new ArrayList<Future<?>>();
                for (var i = 0; i < records.size(); i++) {
                    var index = i;
                    futures.add(pool.submit(() -> {
                        I item = toItem(inSerdes, records.get(index).body(), itemType);
                        outputs[index] = outSerdes.serialize(func.apply(item));
                        return null;
                    }));
                }
                for (var i = 0; i < futures.size(); i++) {
                    try {
                        futures.get(i).get();
                    } catch (ExecutionException e) {
                        errors[i] = e.getCause() != null ? e.getCause() : e;
                    }
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Distributed map item handler interrupted", e);
            } finally {
                pool.shutdown();
            }

            var results = new ArrayList<ItemResult>();
            var failures = new ArrayList<ItemFailure>();
            for (var i = 0; i < records.size(); i++) {
                var itemId = records.get(i).itemId();
                if (errors[i] != null) {
                    failures.add(new ItemFailure(itemId, ItemFailure.ErrorDetail.of(errors[i])));
                } else if (report == ResponseMode.ITEM_RESULTS) {
                    results.add(new ItemResult(itemId, outputs[i]));
                }
            }
            return report == ResponseMode.ITEM_RESULTS
                    ? ProcessorResponse.itemResults(results, failures)
                    : ProcessorResponse.itemFailures(failures);
        };
    }

    /** Wraps a whole-batch function as a processor handler. Returning succeeds every item, throwing fails the batch. */
    public static <I> RequestHandler<ProcessorEvent, Void> createDistributedMapBatchHandler(
            Consumer<List<I>> func, TypeToken<I> itemType, SerDes itemSerDes) {
        var serdes = itemSerDes != null ? itemSerDes : JSON;
        return (event, context) -> {
            func.accept(toItems(serdes, event, itemType));
            return null;
        };
    }

    /** Wraps a reader function as a source handler. A null nextState signals the source is exhausted. */
    public static <I, S> RequestHandler<ReaderEvent, ReaderResponse<I>> createDistributedMapReader(
            Function<S, ReaderPage<I, S>> func, TypeToken<S> stateType, SerDes stateSerDes) {
        var serdes = stateSerDes != null ? stateSerDes : JSON;
        return (event, context) -> {
            S state = event.state() != null ? serdes.deserialize(event.state(), stateType) : null;

            var page = func.apply(state);
            if (page.items().size() > event.maxItems()) {
                throw new IllegalStateException(
                        "reader returned " + page.items().size() + " items, exceeding maxItems " + event.maxItems());
            }
            String nextState = null;
            if (page.nextState() != null) {
                nextState = serdes.serialize(page.nextState());
                if (nextState.getBytes(StandardCharsets.UTF_8).length > READER_STATE_LIMIT) {
                    throw new IllegalStateException(
                            "reader nextState exceeds the " + (READER_STATE_LIMIT / 1024) + " KB limit");
                }
            }
            return new ReaderResponse<>(page.items(), nextState);
        };
    }

    /** Durable variant of the item handler. The function receives the per-item DurableContext. */
    public static <I, O>
            RequestHandler<DurableExecutionInput, DurableExecutionOutput>
                    createDistributedMapItemHandlerWithDurableExecution(
                            BiFunction<DurableContext, I, O> func,
                            TypeToken<I> itemType,
                            SerDes itemSerDes,
                            SerDes resultSerDes,
                            ResponseMode report) {
        validateItemReport(report);
        var inSerdes = itemSerDes != null ? itemSerDes : JSON;
        var outSerdes = resultSerDes != null ? resultSerDes : JSON;
        return DurableExecutor.wrap(
                EVENT_TYPE,
                (event, ctx) -> {
                    var records = event.records();
                    var bodies = new ArrayList<String>(records.size());
                    for (var record : records) {
                        bodies.add(record.body());
                    }
                    MapResult<String> batch = ctx.map(
                            ITEMS_OP_NAME,
                            bodies,
                            OUTPUT_TYPE,
                            (body, index, mapContext) ->
                                    outSerdes.serialize(func.apply(mapContext, toItem(inSerdes, body, itemType))),
                            MapConfig.builder()
                                    .completionConfig(CompletionConfig.allCompleted())
                                    .build());

                    var results = new ArrayList<ItemResult>();
                    var failures = new ArrayList<ItemFailure>();
                    for (var i = 0; i < records.size(); i++) {
                        var itemId = records.get(i).itemId();
                        var item = batch.getItem(i);
                        if (item.status() == MapResult.MapResultItem.Status.SUCCEEDED) {
                            if (report == ResponseMode.ITEM_RESULTS) {
                                results.add(new ItemResult(itemId, item.result()));
                            }
                        } else {
                            var error = item.error();
                            failures.add(new ItemFailure(
                                    itemId,
                                    error != null
                                            ? new ItemFailure.ErrorDetail(error.errorType(), error.errorMessage())
                                            : null));
                        }
                    }
                    return report == ResponseMode.ITEM_RESULTS
                            ? ProcessorResponse.itemResults(results, failures)
                            : ProcessorResponse.itemFailures(failures);
                },
                DurableConfig.defaultConfig());
    }

    /** Durable variant of the batch handler. The function receives the DurableContext. */
    public static <I>
            RequestHandler<DurableExecutionInput, DurableExecutionOutput>
                    createDistributedMapBatchHandlerWithDurableExecution(
                            BiConsumer<DurableContext, List<I>> func, TypeToken<I> itemType, SerDes itemSerDes) {
        var serdes = itemSerDes != null ? itemSerDes : JSON;
        return DurableExecutor.wrap(
                EVENT_TYPE,
                (event, ctx) -> {
                    func.accept(ctx, toItems(serdes, event, itemType));
                    return null;
                },
                DurableConfig.defaultConfig());
    }

    private static <I> List<I> toItems(SerDes serdes, ProcessorEvent event, TypeToken<I> itemType) {
        var records = event.records();
        var items = new ArrayList<I>(records.size());
        for (var record : records) {
            items.add(toItem(serdes, record.body(), itemType));
        }
        return items;
    }

    private static <I> I toItem(SerDes serdes, String body, TypeToken<I> itemType) {
        return serdes.deserialize(body, itemType);
    }
}
