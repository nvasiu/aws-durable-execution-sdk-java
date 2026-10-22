// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.model;

/** Terminal status of a distributed map run. */
public enum DistributedMapStatus {
    SUCCEEDED("SUCCEEDED"),
    FAILED("FAILED"),
    STOPPED("STOPPED"),
    TIMED_OUT("TIMED_OUT");

    private final String value;

    DistributedMapStatus(String value) {
        this.value = value;
    }

    /** Returns the wire-format string value. */
    public String getValue() {
        return value;
    }

    /** Returns the status matching a wire-format string value. */
    public static DistributedMapStatus fromValue(String value) {
        for (var status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown DistributedMapStatus: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
