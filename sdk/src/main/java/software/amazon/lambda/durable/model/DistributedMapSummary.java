// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.model;

import software.amazon.lambda.durable.exception.DistributedMapError;

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

    /** Returns the run id derived from the ARN, or null when the run never started. */
    public String distributedMapId() {
        return distributedMapRunArn != null
                ? distributedMapRunArn.substring(distributedMapRunArn.lastIndexOf(':') + 1)
                : null;
    }

    /** Returns true when any item failed. */
    public boolean hasFailure() {
        return failureCount > 0;
    }

    /** Throws DistributedMapError when the run did not fully succeed. */
    public void throwIfError() {
        if (status != DistributedMapStatus.SUCCEEDED || hasFailure()) {
            throw DistributedMapError.runLevel(status, completionReason, failureCount, completionDetails);
        }
    }
}
