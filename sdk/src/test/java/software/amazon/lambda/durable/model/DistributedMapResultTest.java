// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.lambda.durable.exception.DistributedMapException;

class DistributedMapResultTest {

    private static DistributedMapSummary summary(DistributedMapStatus status, long failureCount) {
        return new DistributedMapSummary(
                status,
                DistributedMapCompletionReason.ALL_COMPLETED,
                2,
                failureCount,
                1,
                "arn:aws:states:us-east-1:123456789012:mapRun:run-42",
                "details",
                4L);
    }

    private static DistributedMapResult.ItemError itemError(String message) {
        return new DistributedMapResult.ItemError("java.lang.RuntimeException", message);
    }

    @Test
    void succeeded_returnsAllSucceededItems() {
        var result = new DistributedMapResult<>(
                summary(DistributedMapStatus.SUCCEEDED, 1),
                List.of(
                        DistributedMapResult.Item.succeeded("a", "out-a"),
                        DistributedMapResult.Item.failed("b", itemError("fail-b")),
                        DistributedMapResult.Item.succeeded("c", "out-c")));

        var succeeded = result.succeeded();
        assertEquals(2, succeeded.size());
        assertEquals("a", succeeded.get(0).itemId());
        assertEquals("c", succeeded.get(1).itemId());
    }

    @Test
    void failed_returnsAllFailedItems() {
        var result = new DistributedMapResult<>(
                summary(DistributedMapStatus.SUCCEEDED, 1),
                List.of(
                        DistributedMapResult.Item.succeeded("a", "out-a"),
                        DistributedMapResult.Item.failed("b", itemError("fail-b"))));

        var failed = result.failed();
        assertEquals(1, failed.size());
        assertEquals("b", failed.get(0).itemId());
    }

    @Test
    void getResults_preservesNullOutputs() {
        var result = new DistributedMapResult<>(
                summary(DistributedMapStatus.SUCCEEDED, 0),
                List.of(
                        DistributedMapResult.Item.succeeded("a", "out-a"),
                        DistributedMapResult.Item.succeeded("b", null),
                        DistributedMapResult.Item.succeeded("c", "out-c")));

        // cardinality must match succeeded() so callers can align outputs with items
        assertEquals(3, result.getResults().size());
        assertEquals(Arrays.asList("out-a", null, "out-c"), result.getResults());
        assertEquals(result.succeeded().size(), result.getResults().size());
    }

    @Test
    void getErrors_filtersOutNullErrors() {
        DistributedMapResult.Item<String> withError = DistributedMapResult.Item.failed("a", itemError("fail-a"));
        var withoutError =
                new DistributedMapResult.Item<String>("b", DistributedMapResult.Item.Status.FAILED, null, null);
        var result = new DistributedMapResult<String>(
                summary(DistributedMapStatus.SUCCEEDED, 2), List.of(withError, withoutError));

        var errors = result.getErrors();
        assertEquals(1, errors.size());
        assertEquals("fail-a", errors.get(0).errorMessage());
    }

    @Test
    void throwIfError_throwsRunLevelOnNonSucceededStatus() {
        var result = new DistributedMapResult<>(
                summary(DistributedMapStatus.FAILED, 0), List.of(DistributedMapResult.Item.succeeded("a", "out-a")));

        var error = assertThrows(DistributedMapException.class, result::throwIfError);
        assertEquals(DistributedMapStatus.FAILED, error.status());
    }

    @Test
    void throwIfError_surfacesFirstFailedItemWhenSucceededWithFailures() {
        var result = new DistributedMapResult<>(
                summary(DistributedMapStatus.SUCCEEDED, 1),
                List.of(
                        DistributedMapResult.Item.succeeded("a", "out-a"),
                        DistributedMapResult.Item.failed("b", itemError("boom-b"))));

        var error = assertThrows(DistributedMapException.class, result::throwIfError);
        assertTrue(error.getMessage().contains("boom-b"));
    }

    @Test
    void throwIfError_doesNothingOnFullSuccess() {
        var result = new DistributedMapResult<>(
                summary(DistributedMapStatus.SUCCEEDED, 0),
                List.of(
                        DistributedMapResult.Item.succeeded("a", "out-a"),
                        DistributedMapResult.Item.succeeded("b", "out-b")));

        assertDoesNotThrow(result::throwIfError);
    }

    @Test
    void delegators_returnComposedSummaryValues() {
        var summary = summary(DistributedMapStatus.SUCCEEDED, 1);
        var result = new DistributedMapResult<>(summary, List.of(DistributedMapResult.Item.succeeded("a", "out-a")));

        assertEquals(summary.status(), result.status());
        assertEquals(summary.completionReason(), result.completionReason());
        assertEquals(summary.successCount(), result.successCount());
        assertEquals(summary.failureCount(), result.failureCount());
        assertEquals(summary.unprocessedCount(), result.unprocessedCount());
        assertEquals(summary.totalCount(), result.totalCount());
        assertEquals(summary.distributedMapRunArn(), result.distributedMapRunArn());
        assertEquals(summary.completionDetails(), result.completionDetails());
        assertEquals(summary.hasFailure(), result.hasFailure());
    }

    @Test
    void items_defaultToEmptyWhenNull() {
        var result = new DistributedMapResult<String>(summary(DistributedMapStatus.SUCCEEDED, 0), null);

        assertTrue(result.items().isEmpty());
    }

    @Test
    void of_usesFullyQualifiedClassName() {
        var error = DistributedMapResult.ItemError.of(new IllegalStateException("boom"));

        assertEquals("java.lang.IllegalStateException", error.errorType());
        assertEquals("boom", error.errorMessage());
    }

    @Test
    void of_preservesNullMessage() {
        var error = DistributedMapResult.ItemError.of(new RuntimeException());

        assertEquals("java.lang.RuntimeException", error.errorType());
        assertNull(error.errorMessage());
    }
}
