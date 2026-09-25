// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.operation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.lambda.model.DistributedMapDetails;
import software.amazon.awssdk.services.lambda.model.DistributedMapOptions;
import software.amazon.awssdk.services.lambda.model.Operation;
import software.amazon.awssdk.services.lambda.model.OperationStatus;
import software.amazon.lambda.durable.TypeToken;
import software.amazon.lambda.durable.context.DurableContextImpl;
import software.amazon.lambda.durable.exception.DistributedMapException;
import software.amazon.lambda.durable.exception.IllegalDurableOperationException;
import software.amazon.lambda.durable.execution.ExecutionManager;
import software.amazon.lambda.durable.execution.ThreadContext;
import software.amazon.lambda.durable.execution.ThreadType;
import software.amazon.lambda.durable.model.DistributedMapCompletionReason;
import software.amazon.lambda.durable.model.DistributedMapResult;
import software.amazon.lambda.durable.model.DistributedMapStatus;
import software.amazon.lambda.durable.model.DistributedMapSummary;
import software.amazon.lambda.durable.model.OperationIdentifier;
import software.amazon.lambda.durable.model.OperationSubType;
import software.amazon.lambda.durable.serde.JacksonSerDes;
import software.amazon.lambda.durable.serde.SerDes;

// Written against DistributedMap* shapes not yet in the generated Lambda client, so this does not compile until they
// ship.
class DistributedMapOperationTest {
    private static final String OPERATION_ID = "7";
    private static final String OPERATION_NAME = "test-distributed-map";
    private static final OperationIdentifier OPERATION_IDENTIFIER =
            OperationIdentifier.of(OPERATION_ID, OPERATION_NAME, OperationSubType.DISTRIBUTED_MAP);
    private static final SerDes SER_DES = new JacksonSerDes();

    private ExecutionManager executionManager;
    private DurableContextImpl durableContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @BeforeEach
    void setUp() {
        executionManager = mock(ExecutionManager.class);
        durableContext = mock(DurableContextImpl.class);
        when(durableContext.getExecutionManager()).thenReturn(executionManager);
        when(executionManager.getCurrentThreadContext()).thenReturn(new ThreadContext("root", ThreadType.CONTEXT));
    }

    @Test
    void getReturnsSummaryWhenOperationSucceeded() {
        var details = DistributedMapDetails.builder()
                .status("SUCCEEDED")
                .completionReason("ALL_COMPLETED")
                .successCount(5L)
                .failureCount(0L)
                .unprocessedCount(0L)
                .totalCount(5L)
                .distributedMapRunArn("arn:aws:lambda:us-east-1:123456789012:map-run:abc")
                .build();
        var op = succeededOperation(details);
        when(executionManager.getOperationAndUpdateReplayState(OPERATION_ID)).thenReturn(op);

        var operation = newOperation(TypeToken.get(DistributedMapSummary.class), DistributedMapOperation::toSummary);
        operation.onCheckpointComplete(op);

        var summary = operation.get();
        assertEquals(DistributedMapStatus.SUCCEEDED, summary.status());
        assertEquals(DistributedMapCompletionReason.ALL_COMPLETED, summary.completionReason());
        assertEquals(5L, summary.successCount());
        assertEquals(0L, summary.failureCount());
        assertEquals(5L, summary.totalCount());
        assertEquals("arn:aws:lambda:us-east-1:123456789012:map-run:abc", summary.distributedMapRunArn());
        assertFalse(summary.hasFailure());
    }

    @Test
    void getReturnsResultWhenResultCollecting() {
        var details = DistributedMapDetails.builder()
                .status("SUCCEEDED")
                .completionReason("ALL_COMPLETED")
                .successCount(2L)
                .failureCount(0L)
                .unprocessedCount(0L)
                .totalCount(2L)
                .build();
        var op = succeededOperation(details);
        when(executionManager.getOperationAndUpdateReplayState(OPERATION_ID)).thenReturn(op);

        @SuppressWarnings({"unchecked", "rawtypes"})
        TypeToken<DistributedMapResult<Integer>> resultType = (TypeToken) TypeToken.get(DistributedMapResult.class);
        BiFunction<DistributedMapStatus, DistributedMapDetails, DistributedMapResult<Integer>> resultBuilder =
                (s, d) -> DistributedMapOperation.toResult(s, d, TypeToken.get(Integer.class), SER_DES);

        var operation = newOperation(resultType, resultBuilder);
        operation.onCheckpointComplete(op);

        var result = operation.get();
        assertEquals(DistributedMapStatus.SUCCEEDED, result.status());
        assertEquals(2L, result.successCount());
        assertTrue(result.getResults().isEmpty());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void getResolvesWithSummaryWhenOperationFailed() {
        assertGetResolvesWithSummary(OperationStatus.FAILED, DistributedMapStatus.FAILED);
    }

    @Test
    void getResolvesWithSummaryWhenOperationTimedOut() {
        assertGetResolvesWithSummary(OperationStatus.TIMED_OUT, DistributedMapStatus.TIMED_OUT);
    }

    @Test
    void getResolvesWithSummaryWhenOperationStopped() {
        assertGetResolvesWithSummary(OperationStatus.STOPPED, DistributedMapStatus.STOPPED);
    }

    @Test
    void getTerminatesWhenSucceededWithNoDetails() {
        var op = Operation.builder()
                .id(OPERATION_ID)
                .name(OPERATION_NAME)
                .status(OperationStatus.SUCCEEDED)
                .build();
        when(executionManager.getOperationAndUpdateReplayState(OPERATION_ID)).thenReturn(op);

        var operation = newOperation(TypeToken.get(DistributedMapSummary.class), DistributedMapOperation::toSummary);
        operation.onCheckpointComplete(op);

        assertThrows(IllegalDurableOperationException.class, operation::get);
        verify(executionManager).terminateExecution(any(IllegalDurableOperationException.class));
    }

    @Test
    void getTerminatesWhenTerminalWithNoCompletionReason() {
        var details = DistributedMapDetails.builder()
                .successCount(1L)
                .failureCount(0L)
                .unprocessedCount(0L)
                .build();
        var op = succeededOperation(details);
        when(executionManager.getOperationAndUpdateReplayState(OPERATION_ID)).thenReturn(op);

        var operation = newOperation(TypeToken.get(DistributedMapSummary.class), DistributedMapOperation::toSummary);
        operation.onCheckpointComplete(op);

        assertThrows(IllegalDurableOperationException.class, operation::get);
        verify(executionManager).terminateExecution(any(IllegalDurableOperationException.class));
    }

    @Test
    void getSuspendsUntilCheckpointCompletesThenReturnsSummary()
            throws InterruptedException, ExecutionException, TimeoutException {
        var details = DistributedMapDetails.builder()
                .status("SUCCEEDED")
                .completionReason("ALL_COMPLETED")
                .successCount(1L)
                .failureCount(0L)
                .unprocessedCount(0L)
                .build();
        var op = succeededOperation(details);
        when(executionManager.getOperationAndUpdateReplayState(OPERATION_ID)).thenReturn(op);

        var operation = newOperation(TypeToken.get(DistributedMapSummary.class), DistributedMapOperation::toSummary);

        // get blocks because the operation has not reached a terminal checkpoint yet
        var future = executor.submit(operation::get);
        try {
            future.get(500, TimeUnit.MILLISECONDS);
            fail();
        } catch (TimeoutException e) {
            // feed the terminal checkpoint, which unblocks get and resolves the summary
            operation.onCheckpointComplete(op);
            var summary = future.get();
            assertEquals(DistributedMapStatus.SUCCEEDED, summary.status());
            assertEquals(1L, summary.successCount());
        }
    }

    // A terminal-failed operation resolves with its summary so the counts are reachable.
    private void assertGetResolvesWithSummary(OperationStatus status, DistributedMapStatus expected) {
        var details = DistributedMapDetails.builder()
                .completionReason("FAILURE_TOLERANCE_EXCEEDED")
                .successCount(3L)
                .failureCount(2L)
                .unprocessedCount(1L)
                .build();
        var op = Operation.builder()
                .id(OPERATION_ID)
                .name(OPERATION_NAME)
                .status(status)
                .distributedMapDetails(details)
                .build();
        when(executionManager.getOperationAndUpdateReplayState(OPERATION_ID)).thenReturn(op);

        var operation = newOperation(TypeToken.get(DistributedMapSummary.class), DistributedMapOperation::toSummary);
        operation.onCheckpointComplete(op);

        var summary = operation.get();
        assertEquals(expected, summary.status());
        assertEquals(DistributedMapCompletionReason.FAILURE_TOLERANCE_EXCEEDED, summary.completionReason());
        assertEquals(3L, summary.successCount());
        assertEquals(2L, summary.failureCount());
        assertEquals(1L, summary.unprocessedCount());
        assertTrue(summary.hasFailure());
        assertThrows(DistributedMapException.class, summary::throwIfError);
    }

    private <R> DistributedMapOperation<R> newOperation(
            TypeToken<R> resultType, BiFunction<DistributedMapStatus, DistributedMapDetails, R> resultBuilder) {
        return new DistributedMapOperation<>(
                OPERATION_IDENTIFIER,
                DistributedMapOptions.builder().build(),
                resultType,
                SER_DES,
                resultBuilder,
                durableContext);
    }

    private Operation succeededOperation(DistributedMapDetails details) {
        return Operation.builder()
                .id(OPERATION_ID)
                .name(OPERATION_NAME)
                .status(OperationStatus.SUCCEEDED)
                .distributedMapDetails(details)
                .build();
    }
}
