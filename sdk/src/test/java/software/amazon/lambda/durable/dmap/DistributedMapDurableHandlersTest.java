// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.dmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.lambda.model.CheckpointUpdatedExecutionState;
import software.amazon.awssdk.services.lambda.model.ExecutionDetails;
import software.amazon.awssdk.services.lambda.model.Operation;
import software.amazon.awssdk.services.lambda.model.OperationStatus;
import software.amazon.awssdk.services.lambda.model.OperationType;
import software.amazon.lambda.durable.DurableConfig;
import software.amazon.lambda.durable.DurableContext;
import software.amazon.lambda.durable.TestUtils;
import software.amazon.lambda.durable.TypeToken;
import software.amazon.lambda.durable.model.DurableExecutionInput;
import software.amazon.lambda.durable.model.ExecutionStatus;
import software.amazon.lambda.durable.serde.JacksonSerDes;
import software.amazon.lambda.durable.serde.SerDes;

/**
 * Tests that the durable processor wrappers run under the durable execution config the caller supplies. The config is
 * how a durable processor registers plugins or supplies its own client, so the wrapper must not force the default.
 */
class DistributedMapDurableHandlersTest {
    private static final String EXECUTION_OP_ID = "f3d7b0c0-1234-5678-90ab-cdef12345678";
    private static final String EXECUTION_ARN =
            "arn:aws:lambda:us-east-1:123456789012:function:test" + "/durable-execution/exec-name/" + EXECUTION_OP_ID;

    /** Counts deserialize calls, so a test can prove the config's own serdes read the event. */
    private static final class RecordingSerDes implements SerDes {
        private final SerDes delegate = new JacksonSerDes();
        private final AtomicInteger deserializeCount = new AtomicInteger();

        @Override
        public String serialize(Object value) {
            return delegate.serialize(value);
        }

        @Override
        public <T> T deserialize(String data, TypeToken<T> typeToken) {
            deserializeCount.incrementAndGet();
            return delegate.deserialize(data, typeToken);
        }
    }

    private static DurableConfig configWith(SerDes serDes) {
        return DurableConfig.builder()
                .withDurableExecutionClient(TestUtils.createMockClient())
                .withSerDes(serDes)
                .build();
    }

    // A first invocation of a durable function: one STARTED execution operation carrying the processor batch.
    private static DurableExecutionInput durableInput(ProcessorEvent event) {
        var executionOp = Operation.builder()
                .id(EXECUTION_OP_ID)
                .type(OperationType.EXECUTION)
                .status(OperationStatus.STARTED)
                .startTimestamp(Instant.parse("2026-08-15T00:00:00Z"))
                .executionDetails(ExecutionDetails.builder()
                        .inputPayload(new JacksonSerDes().serialize(event))
                        .build())
                .build();
        return new DurableExecutionInput(
                EXECUTION_ARN,
                "token-1",
                CheckpointUpdatedExecutionState.builder()
                        .operations(List.of(executionOp))
                        .build());
    }

    private static ProcessorEvent event(String... bodies) {
        var records = new java.util.ArrayList<ProcessorRecord>();
        for (var i = 0; i < bodies.length; i++) {
            records.add(new ProcessorRecord(String.valueOf(i), bodies[i]));
        }
        return new ProcessorEvent(records);
    }

    @Test
    void durableBatchHandlerRunsUnderTheSuppliedConfig() {
        var serDes = new RecordingSerDes();
        var seen = new java.util.ArrayList<Integer>();
        BiConsumer<DurableContext, List<Integer>> func = (ctx, items) -> seen.addAll(items);

        var handler = DistributedMapHandlers.createDistributedMapBatchHandlerWithDurableExecution(
                func, TypeToken.get(Integer.class), null, configWith(serDes));
        var output = handler.handleRequest(durableInput(event("1", "2")), null);

        assertEquals(ExecutionStatus.SUCCEEDED, output.status());
        assertEquals(List.of(1, 2), seen);
        // The default config has its own serdes, so a call here means the supplied config reached the execution.
        assertTrue(serDes.deserializeCount.get() > 0);
    }

    @Test
    void durableItemHandlerRunsUnderTheSuppliedConfig() {
        var serDes = new RecordingSerDes();
        BiFunction<DurableContext, Integer, Integer> func = (ctx, item) -> item * 2;

        var handler = DistributedMapHandlers.createDistributedMapItemHandlerWithDurableExecution(
                func, TypeToken.get(Integer.class), null, null, configWith(serDes));
        var output = handler.handleRequest(durableInput(event("1", "2")), null);

        assertEquals(ExecutionStatus.SUCCEEDED, output.status());
        assertTrue(serDes.deserializeCount.get() > 0);
    }
}
