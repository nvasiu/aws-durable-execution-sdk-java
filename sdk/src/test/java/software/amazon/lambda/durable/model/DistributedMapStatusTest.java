// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DistributedMapStatusTest {

    @Test
    void fromValue_returnsMatchingStatus() {
        assertEquals(DistributedMapStatus.SUCCEEDED, DistributedMapStatus.fromValue("SUCCEEDED"));
        assertEquals(DistributedMapStatus.FAILED, DistributedMapStatus.fromValue("FAILED"));
        assertEquals(DistributedMapStatus.STOPPED, DistributedMapStatus.fromValue("STOPPED"));
        assertEquals(DistributedMapStatus.TIMED_OUT, DistributedMapStatus.fromValue("TIMED_OUT"));
    }

    @Test
    void fromValue_throwsOnUnknown() {
        assertThrows(IllegalArgumentException.class, () -> DistributedMapStatus.fromValue("NOPE"));
    }

    @Test
    void getValue_returnsWireString() {
        assertEquals("SUCCEEDED", DistributedMapStatus.SUCCEEDED.getValue());
    }

    @Test
    void toString_returnsWireString() {
        assertEquals("TIMED_OUT", DistributedMapStatus.TIMED_OUT.toString());
    }
}
