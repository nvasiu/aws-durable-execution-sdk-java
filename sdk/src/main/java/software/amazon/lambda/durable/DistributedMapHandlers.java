// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable;

import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import software.amazon.lambda.durable.config.CompletionConfig;
import software.amazon.lambda.durable.config.DistributedMapProcessor.ResponseMode;
import software.amazon.lambda.durable.config.MapConfig;
import software.amazon.lambda.durable.execution.DurableExecutor;
import software.amazon.lambda.durable.model.DurableExecutionInput;
import software.amazon.lambda.durable.model.DurableExecutionOutput;
import software.amazon.lambda.durable.model.MapResult;
import software.amazon.lambda.durable.model.ReaderPage;
import software.amazon.lambda.durable.serde.JacksonSerDes;
import software.amazon.lambda.durable.serde.SerDes;

/** Authoring helpers that wrap a customer function into a distributed map processor or reader Lambda handler. */
public final class DistributedMapHandlers {
    private static final SerDes JSON = new JacksonSerDes();
    private static final TypeToken<Object> OBJECT_TYPE = TypeToken.get(Object.class);
    private static final TypeToken<Map<String, Object>> EVENT_TYPE = new TypeToken<Map<String, Object>>() {};
    private static final int READER_STATE_LIMIT = 32 * 1024;
    private static final String ITEMS_OP_NAME = "distributed-map-items";

    private DistributedMapHandlers() {}

    private static void validateItemReport(ResponseMode report) {
        if (report != ResponseMode.REPORT_ITEM_RESULTS && report != ResponseMode.REPORT_FAILED_ITEMS) {
            throw new IllegalArgumentException(
                    "item handler report mode must be REPORT_ITEM_RESULTS or REPORT_FAILED_ITEMS, got: " + report);
        }
    }

    /** Wraps a per-item function as a processor handler. Use REPORT_ITEM_RESULTS or REPORT_FAILED_ITEMS. */
    public static <I, O> RequestHandler<Map<String, Object>, Map<String, Object>> createDistributedMapItemHandler(
            Function<I, O> func,
            TypeToken<I> itemType,
            SerDes itemSerDes,
            SerDes resultSerDes,
            ResponseMode report,
            int concurrency) {
        validateItemReport(report);
        var inSerdes = itemSerDes != null ? itemSerDes : JSON;
        var outSerdes = resultSerDes != null ? resultSerDes : JSON;
        return (event, context) -> {
            var records = records(event);
            var workers = concurrency > 0 ? concurrency : Math.max(1, records.size());
            var outputs = new Object[records.size()];
            var errors = new Throwable[records.size()];
            var pool = Executors.newFixedThreadPool(workers);
            try {
                var futures = new ArrayList<Future<?>>();
                for (var i = 0; i < records.size(); i++) {
                    var index = i;
                    futures.add(pool.submit(() -> {
                        I item = toItem(inSerdes, records.get(index).get("body"), itemType);
                        outputs[index] = func.apply(item);
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

            var results = new ArrayList<Map<String, Object>>();
            var failures = new ArrayList<Map<String, Object>>();
            for (var i = 0; i < records.size(); i++) {
                var itemId = (String) records.get(i).get("itemId");
                if (errors[i] != null) {
                    failures.add(errorEntry(itemId, errors[i].getClass().getName(), errors[i].getMessage()));
                } else if (report == ResponseMode.REPORT_ITEM_RESULTS) {
                    results.add(resultEntry(itemId, toJsonValue(outSerdes, outputs[i])));
                }
            }
            return itemResponse(report, results, failures);
        };
    }

    /** Wraps a whole-batch function as a processor handler. Returning succeeds every item, throwing fails the batch. */
    public static <I> RequestHandler<Map<String, Object>, Object> createDistributedMapBatchHandler(
            Consumer<List<I>> func, TypeToken<I> itemType, SerDes itemSerDes) {
        var serdes = itemSerDes != null ? itemSerDes : JSON;
        return (event, context) -> {
            func.accept(toItems(serdes, event, itemType));
            return null;
        };
    }

    /** Wraps a reader function as a source handler. A null nextState signals the source is exhausted. */
    public static <I, S> RequestHandler<Map<String, Object>, Map<String, Object>> createDistributedMapReader(
            Function<S, ReaderPage<I, S>> func, TypeToken<S> stateType, SerDes stateSerDes) {
        var serdes = stateSerDes != null ? stateSerDes : JSON;
        return (event, context) -> {
            if (!(event.get("maxItems") instanceof Number maxItemsValue)) {
                throw new IllegalStateException("expected a distributed map reader event carrying a numeric 'maxItems' (got keys: "
                        + event.keySet() + "), the function must be registered as a distributed map reader");
            }
            var rawState = (String) event.get("state");
            S state = rawState != null ? serdes.deserialize(rawState, stateType) : null;
            var maxItems = maxItemsValue.intValue();

            var page = func.apply(state);
            if (page.items().size() > maxItems) {
                throw new IllegalStateException(
                        "reader returned " + page.items().size() + " items, exceeding maxItems " + maxItems);
            }
            var response = new LinkedHashMap<String, Object>();
            response.put("items", page.items());
            if (page.nextState() != null) {
                var nextState = serdes.serialize(page.nextState());
                if (nextState.getBytes(StandardCharsets.UTF_8).length > READER_STATE_LIMIT) {
                    throw new IllegalStateException(
                            "reader nextState exceeds the " + (READER_STATE_LIMIT / 1024) + " KB limit");
                }
                response.put("nextState", nextState);
            }
            return response;
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
                    var records = records(event);
                    var bodies = new ArrayList<Object>(records.size());
                    for (var record : records) {
                        bodies.add(record.get("body"));
                    }
                    MapResult<Object> batch = ctx.map(
                            ITEMS_OP_NAME,
                            bodies,
                            OBJECT_TYPE,
                            (body, index, mapContext) ->
                                    (Object) func.apply(mapContext, toItem(inSerdes, body, itemType)),
                            MapConfig.builder()
                                    .completionConfig(CompletionConfig.allCompleted())
                                    .build());

                    var results = new ArrayList<Map<String, Object>>();
                    var failures = new ArrayList<Map<String, Object>>();
                    for (var i = 0; i < records.size(); i++) {
                        var itemId = (String) records.get(i).get("itemId");
                        var item = batch.getItem(i);
                        if (item.status() == MapResult.MapResultItem.Status.SUCCEEDED) {
                            if (report == ResponseMode.REPORT_ITEM_RESULTS) {
                                results.add(resultEntry(itemId, toJsonValue(outSerdes, item.result())));
                            }
                        } else {
                            var error = item.error();
                            failures.add(errorEntry(
                                    itemId,
                                    error != null ? error.errorType() : "",
                                    error != null ? error.errorMessage() : ""));
                        }
                    }
                    return itemResponse(report, results, failures);
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

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> records(Map<String, Object> event) {
        if (!(event.get("records") instanceof List)) {
            throw new IllegalStateException("expected a distributed map processor event carrying a 'records' array (got keys: "
                    + event.keySet() + "), the function must be triggered by a distributed map, not invoked directly");
        }
        return (List<Map<String, Object>>) event.get("records");
    }

    private static <I> List<I> toItems(SerDes serdes, Map<String, Object> event, TypeToken<I> itemType) {
        var records = records(event);
        var items = new ArrayList<I>(records.size());
        for (var record : records) {
            items.add(toItem(serdes, record.get("body"), itemType));
        }
        return items;
    }

    private static <I> I toItem(SerDes serdes, Object body, TypeToken<I> itemType) {
        return serdes.deserialize(JSON.serialize(body), itemType);
    }

    private static Object toJsonValue(SerDes serdes, Object value) {
        return JSON.deserialize(serdes.serialize(value), OBJECT_TYPE);
    }

    private static Map<String, Object> resultEntry(String itemId, Object output) {
        var entry = new LinkedHashMap<String, Object>();
        entry.put("itemIdentifier", itemId);
        entry.put("output", output);
        return entry;
    }

    private static Map<String, Object> errorEntry(String itemId, String errorType, String errorMessage) {
        var error = new LinkedHashMap<String, Object>();
        error.put("errorType", errorType);
        error.put("errorMessage", errorMessage);
        var entry = new LinkedHashMap<String, Object>();
        entry.put("itemIdentifier", itemId);
        entry.put("error", error);
        return entry;
    }

    private static Map<String, Object> itemResponse(
            ResponseMode report, List<Map<String, Object>> results, List<Map<String, Object>> failures) {
        var response = new LinkedHashMap<String, Object>();
        if (report == ResponseMode.REPORT_FAILED_ITEMS) {
            response.put("batchItemFailures", failures);
        } else {
            response.put("batchItemResults", results);
            response.put("batchItemFailures", failures);
        }
        return response;
    }
}
