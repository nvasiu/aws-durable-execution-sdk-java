// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.config;

import software.amazon.lambda.durable.util.DistributedMapValidation;

/** S3 destination for succeeded distributed map items. */
public record SuccessDestination(
        String bucket, String prefix, boolean includeInput, boolean includeOutput, String expectedBucketOwner) {

    public SuccessDestination {
        DistributedMapValidation.validateBucketOwner(expectedBucketOwner);
        if (!includeInput && !includeOutput) {
            throw new IllegalArgumentException("success destination must include at least one of input or output");
        }
    }
}
