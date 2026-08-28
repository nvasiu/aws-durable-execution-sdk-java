// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import software.amazon.lambda.durable.exception.DistributedMapError;

/** Outcome of a distributed map run with collected per-item results. */
public record DistributedMapResult<O>(DistributedMapSummary summary, List<DistributedMapResultItem<O>> items) {

    /** Applies a defensive copy and defaults. */
    public DistributedMapResult {
        items = items != null ? List.copyOf(items) : Collections.emptyList();
    }

    /** Returns items that succeeded. */
    public List<DistributedMapResultItem<O>> succeeded() {
        return items.stream()
                .filter(item -> item.status() == DistributedMapResultItem.Status.SUCCEEDED)
                .toList();
    }

    /** Returns items that failed. */
    public List<DistributedMapResultItem<O>> failed() {
        return items.stream()
                .filter(item -> item.status() == DistributedMapResultItem.Status.FAILED)
                .toList();
    }

    /** Returns the outputs of succeeded items. */
    public List<O> getResults() {
        return succeeded().stream().map(DistributedMapResultItem::output).filter(Objects::nonNull).toList();
    }

    /** Returns the errors of failed items. */
    public List<DistributedMapItemError> getErrors() {
        return failed().stream().map(DistributedMapResultItem::error).filter(Objects::nonNull).toList();
    }

    /** Returns the terminal run status. */
    public DistributedMapStatus status() {
        return summary.status();
    }

    /** Returns the completion reason. */
    public DistributedMapCompletionReason completionReason() {
        return summary.completionReason();
    }

    /** Returns the count of succeeded items. */
    public long successCount() {
        return summary.successCount();
    }

    /** Returns the count of failed items. */
    public long failureCount() {
        return summary.failureCount();
    }

    /** Returns the count of unprocessed items. */
    public long unprocessedCount() {
        return summary.unprocessedCount();
    }

    /** Returns the total item count, or null until known. */
    public Long totalCount() {
        return summary.totalCount();
    }

    /** Returns the run ARN, or null when the run never started. */
    public String distributedMapRunArn() {
        return summary.distributedMapRunArn();
    }

    /** Returns the run id derived from the ARN, or null when the run never started. */
    public String distributedMapId() {
        return summary.distributedMapId();
    }

    /** Returns the completion details, or null when absent. */
    public String completionDetails() {
        return summary.completionDetails();
    }

    /** Returns true when any item failed. */
    public boolean hasFailure() {
        return summary.hasFailure();
    }

    /** Throws DistributedMapError when the run did not fully succeed, surfacing the first failed item. */
    public void throwIfError() {
        if (summary.status() != DistributedMapStatus.SUCCEEDED) {
            summary.throwIfError();
            return;
        }
        var failedItems = failed();
        if (!failedItems.isEmpty()) {
            var first = failedItems.get(0);
            throw DistributedMapError.itemLevel(
                    summary.status(),
                    summary.completionReason(),
                    summary.failureCount(),
                    first.error(),
                    first.itemId());
        }
        if (summary.hasFailure()) {
            summary.throwIfError();
        }
    }
}
