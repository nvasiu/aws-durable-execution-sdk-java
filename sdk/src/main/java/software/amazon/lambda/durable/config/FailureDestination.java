// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.config;

import software.amazon.lambda.durable.util.DistributedMapValidation;

/** S3 destination for permanently-failed distributed map items. */
public record FailureDestination(
        String bucket, String prefix, boolean includeInput, boolean includeError, String expectedBucketOwner) {

    public FailureDestination {
        DistributedMapValidation.validateBucketOwner(expectedBucketOwner);
        if (!includeInput && !includeError) {
            throw new IllegalArgumentException("failure destination must include at least one of input or error");
        }
    }
}
