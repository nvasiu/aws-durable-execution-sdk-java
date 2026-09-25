// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.config;

import java.time.Duration;
import software.amazon.lambda.durable.util.DistributedMapValidation;

/** Processor configuration for a distributed map run. */
public class DistributedMapProcessor {

    /** Sentinel for unbounded retry attempts (wire value -1). */
    public static final int UNLIMITED = -1;

    /** Per-batch response mode reported by the processor. */
    public enum ResponseMode {
        BATCH(null),
        ITEM_FAILURES("REPORT_BATCH_ITEM_FAILURES"),
        ITEM_RESULTS("REPORT_BATCH_ITEM_RESULTS");

        private final String value;

        ResponseMode(String value) {
            this.value = value;
        }

        /** Returns the wire functionResponseTypes value, or null for the batch-outcome mode. */
        public String getValue() {
            return value;
        }
    }

    private static final int MIN_BATCH_SIZE = 1;
    private static final int MAX_BATCH_SIZE = 10000;
    private static final int MAX_NAME_PREFIX_LENGTH = 36;
    private static final long MIN_RETRY_DURATION_SECONDS = 60;
    private static final long MAX_RETRY_DURATION_SECONDS = 21600;

    private final String functionName;
    private final ResponseMode responseMode;
    private final Integer batchSize;
    private final Integer maxRetryAttempts;
    private final Duration maxRetryDuration;
    private final String durableExecutionNamePrefix;

    private DistributedMapProcessor(Builder builder) {
        DistributedMapValidation.validateFunctionName(builder.functionName);
        if (builder.batchSize != null && (builder.batchSize < MIN_BATCH_SIZE || builder.batchSize > MAX_BATCH_SIZE)) {
            throw new IllegalArgumentException("batchSize must be between 1 and 10000, got: " + builder.batchSize);
        }
        if (builder.durableExecutionNamePrefix != null
                && (builder.durableExecutionNamePrefix.isEmpty()
                        || builder.durableExecutionNamePrefix.length() > MAX_NAME_PREFIX_LENGTH)) {
            throw new IllegalArgumentException("durableExecutionNamePrefix must be between 1 and 36 characters, got: "
                    + builder.durableExecutionNamePrefix.length());
        }
        this.functionName = builder.functionName;
        this.responseMode = builder.responseMode;
        this.batchSize = builder.batchSize;
        this.maxRetryAttempts = builder.maxRetryAttempts;
        this.maxRetryDuration = builder.maxRetryDuration;
        this.durableExecutionNamePrefix = builder.durableExecutionNamePrefix;
    }

    public String functionName() {
        return functionName;
    }

    public ResponseMode responseMode() {
        return responseMode;
    }

    public Integer batchSize() {
        return batchSize;
    }

    /** Returns the max retry attempts, UNLIMITED for unbounded, or null for the default. */
    public Integer maxRetryAttempts() {
        return maxRetryAttempts;
    }

    /** Returns the cumulative retry duration budget, or null for the default. */
    public Duration maxRetryDuration() {
        return maxRetryDuration;
    }

    public String durableExecutionNamePrefix() {
        return durableExecutionNamePrefix;
    }

    /** Processor that reports a single pass/fail outcome for the whole batch, with no per-item results. */
    public static Builder batch(String functionName) {
        return new Builder(functionName, ResponseMode.BATCH);
    }

    /** Processor that reports the ids of failed items, with all others marked succeeded. */
    public static Builder itemFailures(String functionName) {
        return new Builder(functionName, ResponseMode.ITEM_FAILURES);
    }

    /** Processor that reports the results (output or error) for every item. */
    public static Builder itemResults(String functionName) {
        return new Builder(functionName, ResponseMode.ITEM_RESULTS);
    }

    /** Builder for DistributedMapProcessor. */
    public static class Builder {
        private final String functionName;
        private final ResponseMode responseMode;
        private Integer batchSize;
        private Integer maxRetryAttempts;
        private Duration maxRetryDuration;
        private String durableExecutionNamePrefix;

        private Builder(String functionName, ResponseMode responseMode) {
            this.functionName = functionName;
            this.responseMode = responseMode;
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder maxRetryAttempts(int maxRetryAttempts) {
            if (maxRetryAttempts < 0 && maxRetryAttempts != UNLIMITED) {
                throw new IllegalArgumentException(
                        "maxRetryAttempts must be non-negative or DistributedMapProcessor.UNLIMITED, got: "
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

        public Builder durableExecutionNamePrefix(String durableExecutionNamePrefix) {
            this.durableExecutionNamePrefix = durableExecutionNamePrefix;
            return this;
        }

        public DistributedMapProcessor build() {
            return new DistributedMapProcessor(this);
        }
    }
}
