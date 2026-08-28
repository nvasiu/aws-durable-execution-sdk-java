// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.config;

import java.time.Duration;
import software.amazon.lambda.durable.serde.SerDes;

/** Optional configuration for a distributed map run. */
public class DistributedMapConfig {
    private static final long MAX_TIMEOUT_SECONDS = 7776000; // 90 days

    private final DistributedMapDestinationConfig destination;
    private final DistributedMapCompletionConfig completionConfig;
    private final Duration timeout;
    private final SerDes resultSerDes;

    private DistributedMapConfig(Builder builder) {
        if (builder.timeout != null
                && (builder.timeout.toSeconds() <= 0 || builder.timeout.toSeconds() > MAX_TIMEOUT_SECONDS)) {
            throw new IllegalArgumentException(
                    "timeout must be positive and at most 90 days, got: " + builder.timeout.toSeconds() + "s");
        }
        this.destination = builder.destination;
        this.completionConfig = builder.completionConfig;
        this.timeout = builder.timeout;
        this.resultSerDes = builder.resultSerDes;
    }

    public DistributedMapDestinationConfig destination() {
        return destination;
    }

    public DistributedMapCompletionConfig completionConfig() {
        return completionConfig;
    }

    public Duration timeout() {
        return timeout;
    }

    public SerDes resultSerDes() {
        return resultSerDes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        var builder = new Builder();
        builder.destination = destination;
        builder.completionConfig = completionConfig;
        builder.timeout = timeout;
        builder.resultSerDes = resultSerDes;
        return builder;
    }

    /** Builder for DistributedMapConfig. */
    public static class Builder {
        private DistributedMapDestinationConfig destination;
        private DistributedMapCompletionConfig completionConfig;
        private Duration timeout;
        private SerDes resultSerDes;

        private Builder() {}

        public Builder destination(DistributedMapDestinationConfig destination) {
            this.destination = destination;
            return this;
        }

        public Builder completionConfig(DistributedMapCompletionConfig completionConfig) {
            this.completionConfig = completionConfig;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder resultSerDes(SerDes resultSerDes) {
            this.resultSerDes = resultSerDes;
            return this;
        }

        public DistributedMapConfig build() {
            return new DistributedMapConfig(this);
        }
    }
}
