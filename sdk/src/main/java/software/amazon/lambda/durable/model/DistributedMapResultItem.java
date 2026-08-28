// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.model;

/** Outcome of a single distributed map item. */
public record DistributedMapResultItem<O>(String itemId, Status status, O output, DistributedMapItemError error) {

    /** Status of an individual distributed map item. */
    public enum Status {
        SUCCEEDED,
        FAILED
    }

    /** Creates a succeeded item. */
    public static <O> DistributedMapResultItem<O> succeeded(String itemId, O output) {
        return new DistributedMapResultItem<>(itemId, Status.SUCCEEDED, output, null);
    }

    /** Creates a failed item. */
    public static <O> DistributedMapResultItem<O> failed(String itemId, DistributedMapItemError error) {
        return new DistributedMapResultItem<>(itemId, Status.FAILED, null, error);
    }
}
