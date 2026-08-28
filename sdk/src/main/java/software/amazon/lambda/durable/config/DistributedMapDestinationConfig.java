// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.config;

/** Destination routing for distributed map run results. */
public record DistributedMapDestinationConfig(SuccessDestination onSuccess, FailureDestination onFailure) {

    /** Routes only succeeded item records. */
    public static DistributedMapDestinationConfig onSuccess(SuccessDestination onSuccess) {
        return new DistributedMapDestinationConfig(onSuccess, null);
    }

    /** Routes only failed item records. */
    public static DistributedMapDestinationConfig onFailure(FailureDestination onFailure) {
        return new DistributedMapDestinationConfig(null, onFailure);
    }

    /** Routes both succeeded and failed item records. */
    public static DistributedMapDestinationConfig of(SuccessDestination onSuccess, FailureDestination onFailure) {
        return new DistributedMapDestinationConfig(onSuccess, onFailure);
    }
}
