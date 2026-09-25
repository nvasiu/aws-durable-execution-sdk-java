// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.dmap;

import static org.junit.jupiter.api.Assertions.*;
import static software.amazon.lambda.durable.dmap.DistributedMapHandlers.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.lambda.durable.DurableContext;
import software.amazon.lambda.durable.TypeToken;
import software.amazon.lambda.durable.config.DistributedMapProcessor.ResponseMode;
import software.amazon.lambda.durable.serde.JacksonSerDes;
import software.amazon.lambda.durable.serde.SerDes;

/** Unit tests for the distributed map authoring helpers (non-durable variants plus report validation). */
class DistributedMapHandlersTest {

    // Builds a single processor record. The body is the serialized item, as the wire carries it.
    private static ProcessorRecord rec(String itemId, String body) {
        return new ProcessorRecord(itemId, body);
    }

    // Builds a distributed map processor event carrying the given records.
    private static ProcessorEvent processorEvent(ProcessorRecord... records) {
        return new ProcessorEvent(List.of(records));
    }

    // Builds a distributed map reader event carrying the state and maxItems fields.
    private static ReaderEvent readerEvent(String state, int maxItems) {
        return new ReaderEvent(maxItems, state);
    }

    private static ItemFailure.ErrorDetail error(String type, String message) {
        return new ItemFailure.ErrorDetail(type, message);
    }

    @Test
    void itemHandlerReportsResultsInOrder() {
        Function<Integer, Integer> func = x -> x * 2;
        var handler = createDistributedMapItemHandler(
                func, TypeToken.get(Integer.class), null, null, ResponseMode.ITEM_RESULTS, 1);

        var resp = handler.handleRequest(processorEvent(rec("0", "2"), rec("1", "3")), null);

        assertEquals(List.of(new ItemResult("0", "4"), new ItemResult("1", "6")), resp.batchItemResults());
        assertEquals(List.of(), resp.batchItemFailures());
    }

    @Test
    void itemHandlerCapturesFailuresWithFullyQualifiedErrorType() {
        Function<String, String> func = x -> {
            if (x.equals("bad")) {
                throw new IllegalStateException("boom");
            }
            return x;
        };
        var handler = createDistributedMapItemHandler(
                func, TypeToken.get(String.class), null, null, ResponseMode.ITEM_RESULTS, 1);

        var resp = handler.handleRequest(processorEvent(rec("0", "\"ok\""), rec("1", "\"bad\"")), null);

        assertEquals(List.of(new ItemResult("0", "\"ok\"")), resp.batchItemResults());
        assertEquals(
                List.of(new ItemFailure("1", error("java.lang.IllegalStateException", "boom"))),
                resp.batchItemFailures());
    }

    @Test
    void itemHandlerFailedItemsModeOmitsResults() {
        Function<Integer, Integer> func = x -> x;
        var handler = createDistributedMapItemHandler(
                func, TypeToken.get(Integer.class), null, null, ResponseMode.ITEM_FAILURES, 1);

        var resp = handler.handleRequest(processorEvent(rec("0", "1")), null);

        // Null rather than empty, so the runtime leaves the key off the wire entirely.
        assertNull(resp.batchItemResults());
        assertEquals(List.of(), resp.batchItemFailures());
    }

    @Test
    void itemHandlerFailedItemsModeReportsFailingItem() {
        Function<Integer, Integer> func = x -> {
            throw new IllegalStateException("nope");
        };
        var handler = createDistributedMapItemHandler(
                func, TypeToken.get(Integer.class), null, null, ResponseMode.ITEM_FAILURES, 1);

        var resp = handler.handleRequest(processorEvent(rec("7", "1")), null);

        assertNull(resp.batchItemResults());
        assertEquals(
                List.of(new ItemFailure("7", error("java.lang.IllegalStateException", "nope"))),
                resp.batchItemFailures());
    }

    @Test
    void itemHandlerHonorsRequestedConcurrency() {
        // Each item blocks until all four have arrived, so the batch only finishes if the pool really runs four
        // items at once. At a smaller pool size the first item waits out the timeout and every item fails.
        var barrier = new CyclicBarrier(4);
        Function<Integer, Integer> func = x -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("barrier not reached", e);
            }
            return x;
        };
        var handler = createDistributedMapItemHandler(
                func, TypeToken.get(Integer.class), null, null, ResponseMode.ITEM_RESULTS, 4);

        var resp =
                handler.handleRequest(processorEvent(rec("0", "0"), rec("1", "1"), rec("2", "2"), rec("3", "3")), null);

        assertEquals(List.of(), resp.batchItemFailures());
        // The four items are released together, so completion order is arbitrary while results stay in item order.
        assertEquals(
                List.of(
                        new ItemResult("0", "0"),
                        new ItemResult("1", "1"),
                        new ItemResult("2", "2"),
                        new ItemResult("3", "3")),
                resp.batchItemResults());
    }

    @Test
    void itemHandlerDefaultsToOneConcurrentHandler() {
        var inFlight = new AtomicInteger();
        var peakInFlight = new AtomicInteger();
        Function<String, String> func = x -> {
            peakInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            try {
                // Held briefly so a regression to a wider pool would actually overlap and be caught.
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            inFlight.decrementAndGet();
            return x;
        };
        // The overload with no concurrency argument runs one handler at a time.
        var handler = createDistributedMapItemHandler(
                func, TypeToken.get(String.class), null, null, ResponseMode.ITEM_RESULTS);

        var resp = handler.handleRequest(processorEvent(rec("0", "\"a\""), rec("1", "\"b\""), rec("2", "\"c\"")), null);

        assertEquals(1, peakInFlight.get());
        assertEquals(3, resp.batchItemResults().size());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void itemHandlerRejectsNonPositiveConcurrency(int concurrency) {
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> DistributedMapHandlers.<String, String>createDistributedMapItemHandler(
                        item -> item, TypeToken.get(String.class), null, null, ResponseMode.ITEM_RESULTS, concurrency));
        assertEquals("concurrency must be at least 1, got: " + concurrency, exception.getMessage());
    }

    @Test
    void itemHandlerReportsSerializationFailureAgainstTheOffendingItemOnly() {
        // A result serdes that refuses exactly one value, standing in for a POJO the caller cannot serialize.
        var refusingSerDes = new SerDes() {
            private final SerDes delegate = new JacksonSerDes();

            @Override
            public String serialize(Object value) {
                if ("boom".equals(value)) {
                    throw new IllegalStateException("cannot serialize");
                }
                return delegate.serialize(value);
            }

            @Override
            public <T> T deserialize(String data, TypeToken<T> typeToken) {
                return delegate.deserialize(data, typeToken);
            }
        };

        var handler = DistributedMapHandlers.<String, String>createDistributedMapItemHandler(
                item -> item, TypeToken.get(String.class), null, refusingSerDes, ResponseMode.ITEM_RESULTS, 2);

        var resp = handler.handleRequest(
                processorEvent(rec("a", "\"ok\""), rec("b", "\"boom\""), rec("c", "\"fine\"")), null);

        // The whole batch must not fail. Only item b is reported as failed.
        assertEquals(2, resp.batchItemResults().size());
        assertEquals("a", resp.batchItemResults().get(0).itemIdentifier());
        assertEquals("c", resp.batchItemResults().get(1).itemIdentifier());
        assertEquals(1, resp.batchItemFailures().size());
        assertEquals("b", resp.batchItemFailures().get(0).itemIdentifier());
    }

    @Test
    void itemHandlerRejectsInvalidReportMode() {
        Function<Integer, Integer> func = x -> x;
        assertThrows(
                IllegalArgumentException.class,
                () -> createDistributedMapItemHandler(
                        func, TypeToken.get(Integer.class), null, null, ResponseMode.BATCH, 1));
    }

    @Test
    void processorEventRejectsMissingRecords() {
        var ex = assertThrows(IllegalStateException.class, () -> new ProcessorEvent(null));
        assertTrue(ex.getMessage().contains("'records' array"));
        assertTrue(ex.getMessage().contains("not invoked directly"));
    }

    @Test
    void readerEventRejectsMissingMaxItems() {
        var ex = assertThrows(IllegalStateException.class, () -> new ReaderEvent(null, "state"));
        assertTrue(ex.getMessage().contains("'maxItems'"));
    }

    @Test
    void batchHandlerSucceedsAllItemsWhenFunctionReturns() {
        var seen = new ArrayList<Integer>();
        Consumer<List<Integer>> func = seen::addAll;
        var handler = createDistributedMapBatchHandler(func, TypeToken.get(Integer.class), null);

        var resp = handler.handleRequest(processorEvent(rec("0", "1"), rec("1", "2")), null);

        assertNull(resp);
        assertEquals(List.of(1, 2), seen);
    }

    @Test
    void batchHandlerFailsBatchWhenFunctionThrows() {
        Consumer<List<Integer>> func = items -> {
            throw new RuntimeException("batch failed");
        };
        var handler = createDistributedMapBatchHandler(func, TypeToken.get(Integer.class), null);

        var ex = assertThrows(RuntimeException.class, () -> handler.handleRequest(processorEvent(rec("0", "1")), null));
        assertTrue(ex.getMessage().contains("batch failed"));
    }

    @Test
    void readerReturnsItemsAndNextStateThenExhausts() {
        Function<Integer, ReaderPage<Integer, Integer>> read =
                state -> state == null ? new ReaderPage<>(List.of(1, 2), 1) : new ReaderPage<>(List.of(3), null);
        var handler = createDistributedMapReader(read, TypeToken.get(Integer.class), null);

        var first = handler.handleRequest(readerEvent(null, 10), null);
        assertEquals(List.of(1, 2), first.items());
        assertEquals("1", first.nextState());

        var second = handler.handleRequest(readerEvent("1", 10), null);
        assertEquals(List.of(3), second.items());
        // Null rather than empty, so the runtime omits the key, which is how exhaustion is signalled.
        assertNull(second.nextState());
    }

    @Test
    void readerRejectsPageOverMaxItems() {
        Function<Integer, ReaderPage<Integer, Integer>> read = state -> new ReaderPage<>(List.of(1, 2, 3), null);
        var handler = createDistributedMapReader(read, TypeToken.get(Integer.class), null);

        var ex = assertThrows(IllegalStateException.class, () -> handler.handleRequest(readerEvent(null, 2), null));
        assertTrue(ex.getMessage().contains("exceeding maxItems"));
    }

    @Test
    void readerRejectsOversizedNextState() {
        Function<String, ReaderPage<Integer, String>> read = state -> new ReaderPage<>(List.of(1), "x".repeat(40_000));
        var handler = createDistributedMapReader(read, TypeToken.get(String.class), null);

        var ex = assertThrows(IllegalStateException.class, () -> handler.handleRequest(readerEvent(null, 10), null));
        assertTrue(ex.getMessage().contains("32 KB"));
    }

    @Test
    void durableItemHandlerRejectsInvalidReportMode() {
        BiFunction<DurableContext, Integer, Integer> func = (ctx, item) -> item;
        assertThrows(
                IllegalArgumentException.class,
                () -> createDistributedMapItemHandlerWithDurableExecution(
                        func, TypeToken.get(Integer.class), null, null, ResponseMode.BATCH));
    }

    // Serialization moved out of the SDK into the Lambda runtime, so these assert the wire shape the runtime
    // produces. The mapper mirrors the runtime's null exclusion, which is what keeps an absent key absent.
    private static final ObjectMapper WIRE = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Test
    void itemFailuresResponseLeavesResultsOffTheWire() throws Exception {
        var json = WIRE.writeValueAsString(ProcessorResponse.itemFailures(List.of(new ItemFailure("1", null))));

        assertFalse(json.contains("batchItemResults"));
        assertTrue(json.contains("batchItemFailures"));
    }

    @Test
    void itemFailureNestsTheErrorObject() throws Exception {
        var json = WIRE.writeValueAsString(new ItemFailure("1", error("SomeType", "boom")));

        assertEquals(
                "{\"itemIdentifier\":\"1\",\"error\":{\"errorType\":\"SomeType\",\"errorMessage\":\"boom\"}}", json);
    }

    @Test
    void exhaustedReaderPageLeavesNextStateOffTheWire() throws Exception {
        var json = WIRE.writeValueAsString(new ReaderResponse<>(List.of(1, 2), null));

        assertEquals("{\"items\":[1,2]}", json);
    }
}
