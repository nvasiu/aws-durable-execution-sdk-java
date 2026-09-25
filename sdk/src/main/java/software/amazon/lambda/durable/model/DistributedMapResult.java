// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import software.amazon.lambda.durable.exception.DistributedMapException;

/** Outcome of a distributed map run with collected per-item results. */
public record DistributedMapResult<O>(DistributedMapSummary summary, List<Item<O>> items) {

    /** Applies a defensive copy and defaults. */
    public DistributedMapResult {
        items = items != null ? List.copyOf(items) : Collections.emptyList();
    }

    /** Returns items that succeeded. */
    public List<Item<O>> succeeded() {
        return items.stream()
                .filter(item -> item.status() == Item.Status.SUCCEEDED)
                .toList();
    }

    /** Returns items that failed. */
    public List<Item<O>> failed() {
        return items.stream()
                .filter(item -> item.status() == Item.Status.FAILED)
                .toList();
    }

    /** Returns the outputs of succeeded items (includes null outputs from successful items). */
    public List<O> getResults() {
        return succeeded().stream().map(Item::output).toList();
    }

    /** Returns the errors of failed items. */
    public List<ItemError> getErrors() {
        return failed().stream().map(Item::error).filter(Objects::nonNull).toList();
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

    /** Returns the completion details, or null when absent. */
    public String completionDetails() {
        return summary.completionDetails();
    }

    /** Returns true when any item failed. */
    public boolean hasFailure() {
        return summary.hasFailure();
    }

    /** Throws DistributedMapException when the run did not fully succeed, surfacing the first failed item. */
    public void throwIfError() {
        if (summary.status() != DistributedMapStatus.SUCCEEDED) {
            summary.throwIfError();
            return;
        }
        var failedItems = failed();
        if (!failedItems.isEmpty()) {
            var first = failedItems.get(0);
            throw DistributedMapException.itemLevel(
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

    /** Outcome of a single distributed map item. */
    public record Item<O>(String itemId, Status status, O output, ItemError error) {

        /** Status of an individual distributed map item. */
        public enum Status {
            SUCCEEDED,
            FAILED
        }

        /** Creates a succeeded item. */
        public static <O> Item<O> succeeded(String itemId, O output) {
            return new Item<>(itemId, Status.SUCCEEDED, output, null);
        }

        /** Creates a failed item. */
        public static <O> Item<O> failed(String itemId, ItemError error) {
            return new Item<>(itemId, Status.FAILED, null, error);
        }
    }

    /** Error details for a failed distributed map item. */
    public record ItemError(String errorType, String errorMessage) {

        /** Creates an item error from a throwable. */
        public static ItemError of(Throwable e) {
            return new ItemError(e.getClass().getName(), e.getMessage());
        }
    }
}
