// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.config;

import software.amazon.lambda.durable.util.DistributedMapValidation;

/** Processor configuration for a distributed map run. */
public class DistributedMapProcessor {

    /** Per-batch response mode reported by the processor. */
    public enum ResponseMode {
        REPORT_BATCH_OUTCOME(null),
        REPORT_FAILED_ITEMS("REPORT_BATCH_ITEM_FAILURES"),
        REPORT_ITEM_RESULTS("REPORT_BATCH_ITEM_RESULTS");

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

    private final String functionName;
    private final ResponseMode responseMode;
    private final Integer batchSize;
    private final ProcessorRetryConfig retryConfig;
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
        this.retryConfig = builder.retryConfig;
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

    public ProcessorRetryConfig retryConfig() {
        return retryConfig;
    }

    public String durableExecutionNamePrefix() {
        return durableExecutionNamePrefix;
    }

    /** Processor that reports a single pass/fail outcome for the whole batch, with no per-item results. */
    public static Builder reportBatchOutcome(String functionName) {
        return new Builder(functionName, ResponseMode.REPORT_BATCH_OUTCOME);
    }

    /** Processor that reports the ids of failed items, with all others marked succeeded. */
    public static Builder reportFailedItems(String functionName) {
        return new Builder(functionName, ResponseMode.REPORT_FAILED_ITEMS);
    }

    /** Processor that reports the results (output or error) for every item. */
    public static Builder reportItemResults(String functionName) {
        return new Builder(functionName, ResponseMode.REPORT_ITEM_RESULTS);
    }

    /** Builder for DistributedMapProcessor. */
    public static class Builder {
        private final String functionName;
        private final ResponseMode responseMode;
        private Integer batchSize;
        private ProcessorRetryConfig retryConfig;
        private String durableExecutionNamePrefix;

        private Builder(String functionName, ResponseMode responseMode) {
            this.functionName = functionName;
            this.responseMode = responseMode;
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder retryConfig(ProcessorRetryConfig retryConfig) {
            this.retryConfig = retryConfig;
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
