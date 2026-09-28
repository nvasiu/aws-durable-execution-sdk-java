// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.exception;

import software.amazon.lambda.durable.model.DistributedMapCompletionReason;
import software.amazon.lambda.durable.model.DistributedMapResult;
import software.amazon.lambda.durable.model.DistributedMapStatus;

/**
 * Run outcome error, thrown by throwIfError when a distributed map run did not fully succeed. It reports an aggregate
 * run result and is not a single operation failure, so it stays out of the DurableOperationException hierarchy.
 */
public class DistributedMapException extends DurableExecutionException {
    private final DistributedMapStatus status;
    private final DistributedMapCompletionReason completionReason;
    private final long failureCount;

    private DistributedMapException(
            DistributedMapStatus status,
            DistributedMapCompletionReason completionReason,
            long failureCount,
            String message) {
        super(message);
        this.status = status;
        this.completionReason = completionReason;
        this.failureCount = failureCount;
    }

    /** Run-level failure describing the run status, reason, and failure count. */
    public static DistributedMapException runLevel(
            DistributedMapStatus status,
            DistributedMapCompletionReason completionReason,
            long failureCount,
            String completionDetails) {
        return new DistributedMapException(
                status,
                completionReason,
                failureCount,
                runMessage(status, completionReason, failureCount, completionDetails));
    }

    /** Item-level failure surfacing the first failed item's error. */
    public static DistributedMapException itemLevel(
            DistributedMapStatus status,
            DistributedMapCompletionReason completionReason,
            long failureCount,
            DistributedMapResult.ItemError error,
            String itemId) {
        return new DistributedMapException(status, completionReason, failureCount, itemMessage(error, itemId));
    }

    /** Returns the terminal run status. */
    public DistributedMapStatus status() {
        return status;
    }

    /** Returns the completion reason. */
    public DistributedMapCompletionReason completionReason() {
        return completionReason;
    }

    /** Returns the count of failed items. */
    public long failureCount() {
        return failureCount;
    }

    private static String runMessage(
            DistributedMapStatus status,
            DistributedMapCompletionReason completionReason,
            long failureCount,
            String completionDetails) {
        var message = new StringBuilder("Distributed map run ")
                .append(status)
                .append(" (")
                .append(completionReason)
                .append(")");
        if (failureCount > 0) {
            message.append(", ").append(failureCount).append(" item(s) failed");
        }
        if (completionDetails != null) {
            message.append(": ").append(completionDetails);
        }
        return message.toString();
    }

    private static String itemMessage(DistributedMapResult.ItemError error, String itemId) {
        if (error != null) {
            return error.errorType() + ": " + error.errorMessage();
        }
        return "Distributed map item " + itemId + " failed";
    }
}
