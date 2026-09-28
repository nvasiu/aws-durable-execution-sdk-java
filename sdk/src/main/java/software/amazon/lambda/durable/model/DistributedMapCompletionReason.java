// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.model;

/** Reason a distributed map run reached its terminal status. */
public enum DistributedMapCompletionReason {
    ALL_COMPLETED("ALL_COMPLETED"),
    ITEM_LIMIT_REACHED("ITEM_LIMIT_REACHED"),
    STOPPED("STOPPED"),
    TIMED_OUT("TIMED_OUT"),
    FAILURE_TOLERANCE_EXCEEDED("FAILURE_TOLERANCE_EXCEEDED"),
    SOURCE_FAILED("SOURCE_FAILED"),
    DESTINATION_FAILED("DESTINATION_FAILED"),
    INLINE_RESULT_LIMIT_EXCEEDED("INLINE_RESULT_LIMIT_EXCEEDED"),
    INVALID_CONFIGURATION("INVALID_CONFIGURATION"),
    QUOTA_EXCEEDED("QUOTA_EXCEEDED"),
    KMS_ACCESS_DENIED("KMS_ACCESS_DENIED"),
    INTERNAL_ERROR("INTERNAL_ERROR"),
    /** A value the backend returned that this SDK version does not recognize. */
    UNKNOWN_TO_SDK_VERSION("UNKNOWN_TO_SDK_VERSION");

    private final String value;

    DistributedMapCompletionReason(String value) {
        this.value = value;
    }

    /** Returns the wire-format string value. */
    public String getValue() {
        return value;
    }

    /** Returns the reason matching a wire-format string value, or UNKNOWN_TO_SDK_VERSION if unrecognized. */
    public static DistributedMapCompletionReason fromValue(String value) {
        for (var reason : values()) {
            if (reason.value.equals(value)) {
                return reason;
            }
        }
        return UNKNOWN_TO_SDK_VERSION;
    }

    @Override
    public String toString() {
        return value;
    }
}
