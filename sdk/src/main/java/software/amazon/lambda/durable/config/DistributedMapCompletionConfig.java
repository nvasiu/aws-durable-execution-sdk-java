// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.config;

/** Failure-tolerance configuration for a distributed map run. */
public record DistributedMapCompletionConfig(
        Integer toleratedFailureCount, Double toleratedFailurePercentage, Integer minimumSampleSize) {

    public DistributedMapCompletionConfig {
        if (toleratedFailureCount != null && toleratedFailurePercentage != null) {
            throw new IllegalArgumentException(
                    "toleratedFailureCount and toleratedFailurePercentage are mutually exclusive");
        }
        if (minimumSampleSize != null && toleratedFailurePercentage == null) {
            throw new IllegalArgumentException("minimumSampleSize is only valid with toleratedFailurePercentage");
        }
        if (toleratedFailureCount != null && toleratedFailureCount < 0) {
            throw new IllegalArgumentException(
                    "toleratedFailureCount must be non-negative, got: " + toleratedFailureCount);
        }
        if (toleratedFailurePercentage != null
                && (toleratedFailurePercentage < 0 || toleratedFailurePercentage > 100)) {
            throw new IllegalArgumentException(
                    "toleratedFailurePercentage must be between 0 and 100, got: " + toleratedFailurePercentage);
        }
        if (minimumSampleSize != null && minimumSampleSize < 1) {
            throw new IllegalArgumentException("minimumSampleSize must be at least 1, got: " + minimumSampleSize);
        }
    }

    /** Abort once this many items have permanently failed. */
    public static DistributedMapCompletionConfig failureCount(int count) {
        return new DistributedMapCompletionConfig(count, null, null);
    }

    /** Abort once the failure rate exceeds this percentage (0 to 100). */
    public static DistributedMapCompletionConfig failurePercentage(double percentage) {
        return new DistributedMapCompletionConfig(null, percentage, null);
    }

    /** Abort once the failure rate exceeds this percentage (0 to 100), gated by a minimum sample size. */
    public static DistributedMapCompletionConfig failurePercentage(double percentage, int minimumSampleSize) {
        return new DistributedMapCompletionConfig(null, percentage, minimumSampleSize);
    }
}
