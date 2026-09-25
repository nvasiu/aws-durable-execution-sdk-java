// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DistributedMapCompletionReasonTest {

    @Test
    void fromValue_returnsMatchingReason() {
        assertEquals(
                DistributedMapCompletionReason.ALL_COMPLETED,
                DistributedMapCompletionReason.fromValue("ALL_COMPLETED"));
        assertEquals(
                DistributedMapCompletionReason.FAILURE_TOLERANCE_EXCEEDED,
                DistributedMapCompletionReason.fromValue("FAILURE_TOLERANCE_EXCEEDED"));
    }

    @Test
    void fromValue_returnsUnknownSentinelOnUnknown() {
        assertEquals(
                DistributedMapCompletionReason.UNKNOWN_TO_SDK_VERSION,
                DistributedMapCompletionReason.fromValue("SOMETHING_NEW"));
    }

    @Test
    void getValue_returnsWireString() {
        assertEquals("ALL_COMPLETED", DistributedMapCompletionReason.ALL_COMPLETED.getValue());
    }

    @Test
    void toString_returnsWireString() {
        assertEquals("STOPPED", DistributedMapCompletionReason.STOPPED.toString());
    }
}
