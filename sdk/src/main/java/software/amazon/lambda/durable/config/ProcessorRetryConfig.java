// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.config;

import java.time.Duration;

/** Retry configuration for a distributed map processor. */
public class ProcessorRetryConfig {
    /** Sentinel for unbounded retry attempts (wire value -1). */
    public static final int UNLIMITED = -1;

    private static final long MIN_RETRY_DURATION_SECONDS = 60;
    private static final long MAX_RETRY_DURATION_SECONDS = 21600;

    private final Integer maxRetryAttempts;
    private final Duration maxRetryDuration;

    private ProcessorRetryConfig(Builder builder) {
        this.maxRetryAttempts = builder.maxRetryAttempts;
        this.maxRetryDuration = builder.maxRetryDuration;
    }

    /** Returns the max retry attempts, UNLIMITED for unbounded, or null for the default. */
    public Integer maxRetryAttempts() {
        return maxRetryAttempts;
    }

    /** Returns the cumulative retry duration budget, or null for the default. */
    public Duration maxRetryDuration() {
        return maxRetryDuration;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        var builder = new Builder();
        builder.maxRetryAttempts = maxRetryAttempts;
        builder.maxRetryDuration = maxRetryDuration;
        return builder;
    }

    /** Builder for ProcessorRetryConfig. */
    public static class Builder {
        private Integer maxRetryAttempts;
        private Duration maxRetryDuration;

        private Builder() {}

        public Builder maxRetryAttempts(int maxRetryAttempts) {
            if (maxRetryAttempts < 0 && maxRetryAttempts != UNLIMITED) {
                throw new IllegalArgumentException(
                        "maxRetryAttempts must be non-negative or ProcessorRetryConfig.UNLIMITED, got: "
                                + maxRetryAttempts);
            }
            this.maxRetryAttempts = maxRetryAttempts;
            return this;
        }

        public Builder maxRetryDuration(Duration maxRetryDuration) {
            if (maxRetryDuration != null
                    && (maxRetryDuration.toSeconds() < MIN_RETRY_DURATION_SECONDS
                            || maxRetryDuration.toSeconds() > MAX_RETRY_DURATION_SECONDS)) {
                throw new IllegalArgumentException("maxRetryDuration must be between 1 minute and 6 hours, got: "
                        + maxRetryDuration.toSeconds() + "s");
            }
            this.maxRetryDuration = maxRetryDuration;
            return this;
        }

        public ProcessorRetryConfig build() {
            return new ProcessorRetryConfig(this);
        }
    }
}
