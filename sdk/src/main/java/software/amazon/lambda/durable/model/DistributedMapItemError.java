// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.model;

/** Error details for a failed distributed map item. */
public record DistributedMapItemError(String errorType, String errorMessage) {

    /** Creates an item error from a throwable. */
    public static DistributedMapItemError of(Throwable e) {
        return new DistributedMapItemError(e.getClass().getName(), e.getMessage());
    }
}
