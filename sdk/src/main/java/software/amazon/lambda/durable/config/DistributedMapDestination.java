// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.config;

import software.amazon.lambda.durable.util.DistributedMapValidation;

/** Destination factories for a distributed map run. */
public final class DistributedMapDestination {
    private DistributedMapDestination() {}

    /** Route succeeded item records to an S3 prefix (defaults to include output only). */
    public static SuccessDestination successes(String prefixUri) {
        return successes(prefixUri, false, true, null);
    }

    /** Route succeeded item records to an S3 prefix. */
    public static SuccessDestination successes(
            String prefixUri, boolean includeInput, boolean includeOutput, String expectedBucketOwner) {
        var parsed = DistributedMapValidation.parseS3Uri(prefixUri);
        return new SuccessDestination(
                parsed.bucket(),
                parsed.path() != null ? parsed.path() : "",
                includeInput,
                includeOutput,
                expectedBucketOwner);
    }

    /** Route permanently-failed item records to an S3 prefix (defaults to include input and error). */
    public static FailureDestination failures(String prefixUri) {
        return failures(prefixUri, true, true, null);
    }

    /** Route permanently-failed item records to an S3 prefix. */
    public static FailureDestination failures(
            String prefixUri, boolean includeInput, boolean includeError, String expectedBucketOwner) {
        var parsed = DistributedMapValidation.parseS3Uri(prefixUri);
        return new FailureDestination(
                parsed.bucket(),
                parsed.path() != null ? parsed.path() : "",
                includeInput,
                includeError,
                expectedBucketOwner);
    }
}
