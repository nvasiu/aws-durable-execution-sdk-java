// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.model;

import software.amazon.lambda.durable.exception.DistributedMapException;

/** Outcome of a distributed map run, without per-item results. */
public record DistributedMapSummary(
        DistributedMapStatus status,
        DistributedMapCompletionReason completionReason,
        long successCount,
        long failureCount,
        long unprocessedCount,
        String distributedMapRunArn,
        String completionDetails,
        Long totalCount) {

    /** Returns true when any item failed. */
    public boolean hasFailure() {
        return failureCount > 0;
    }

    /** Throws DistributedMapException when the run did not fully succeed. */
    public void throwIfError() {
        if (status != DistributedMapStatus.SUCCEEDED || hasFailure()) {
            throw DistributedMapException.runLevel(status, completionReason, failureCount, completionDetails);
        }
    }
}
