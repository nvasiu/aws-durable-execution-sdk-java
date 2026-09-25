// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.config;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DistributedMapProcessorTest {

    @Test
    void batch_setsBatchMode() {
        var processor = DistributedMapProcessor.batch("fn").build();

        assertEquals("fn", processor.functionName());
        assertEquals(DistributedMapProcessor.ResponseMode.BATCH, processor.responseMode());
        assertNull(processor.responseMode().getValue());
    }

    @Test
    void itemFailures_setsItemFailuresMode() {
        var processor = DistributedMapProcessor.itemFailures("fn").build();

        assertEquals(DistributedMapProcessor.ResponseMode.ITEM_FAILURES, processor.responseMode());
        assertEquals("REPORT_BATCH_ITEM_FAILURES", processor.responseMode().getValue());
    }

    @Test
    void itemResults_setsItemResultsMode() {
        var processor = DistributedMapProcessor.itemResults("fn").build();

        assertEquals(DistributedMapProcessor.ResponseMode.ITEM_RESULTS, processor.responseMode());
        assertEquals("REPORT_BATCH_ITEM_RESULTS", processor.responseMode().getValue());
    }

    @Test
    void build_defaultsAreNull() {
        var processor = DistributedMapProcessor.batch("fn").build();

        assertNull(processor.batchSize());
        assertNull(processor.maxRetryAttempts());
        assertNull(processor.maxRetryDuration());
        assertNull(processor.durableExecutionNamePrefix());
    }

    @Test
    void batchSize_atBoundaries_shouldPass() {
        assertEquals(1, DistributedMapProcessor.batch("fn").batchSize(1).build().batchSize());
        assertEquals(
                10000,
                DistributedMapProcessor.batch("fn").batchSize(10000).build().batchSize());
    }

    @Test
    void batchSize_belowMinimum_shouldThrow() {
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> DistributedMapProcessor.batch("fn").batchSize(0).build());
        assertEquals("batchSize must be between 1 and 10000, got: 0", exception.getMessage());
    }

    @Test
    void batchSize_aboveMaximum_shouldThrow() {
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> DistributedMapProcessor.batch("fn").batchSize(10001).build());
        assertEquals("batchSize must be between 1 and 10000, got: 10001", exception.getMessage());
    }

    @Test
    void durableExecutionNamePrefix_atBoundaries_shouldPass() {
        assertEquals(
                "a",
                DistributedMapProcessor.batch("fn")
                        .durableExecutionNamePrefix("a")
                        .build()
                        .durableExecutionNamePrefix());
        var thirtySix = "a".repeat(36);
        assertEquals(
                thirtySix,
                DistributedMapProcessor.batch("fn")
                        .durableExecutionNamePrefix(thirtySix)
                        .build()
                        .durableExecutionNamePrefix());
    }

    @Test
    void durableExecutionNamePrefix_empty_shouldThrow() {
        var exception = assertThrows(IllegalArgumentException.class, () -> DistributedMapProcessor.batch("fn")
                .durableExecutionNamePrefix("")
                .build());
        assertEquals("durableExecutionNamePrefix must be between 1 and 36 characters, got: 0", exception.getMessage());
    }

    @Test
    void durableExecutionNamePrefix_tooLong_shouldThrow() {
        var exception = assertThrows(IllegalArgumentException.class, () -> DistributedMapProcessor.batch("fn")
                .durableExecutionNamePrefix("a".repeat(37))
                .build());
        assertEquals("durableExecutionNamePrefix must be between 1 and 36 characters, got: 37", exception.getMessage());
    }

    @Test
    void retryFields_areStored() {
        var processor = DistributedMapProcessor.batch("fn")
                .maxRetryAttempts(3)
                .maxRetryDuration(Duration.ofMinutes(5))
                .build();

        assertEquals(3, processor.maxRetryAttempts());
        assertEquals(Duration.ofMinutes(5), processor.maxRetryDuration());
    }

    @Test
    void unlimitedRetryAttempts_isStored() {
        var processor = DistributedMapProcessor.batch("fn")
                .maxRetryAttempts(DistributedMapProcessor.UNLIMITED)
                .build();

        assertEquals(DistributedMapProcessor.UNLIMITED, processor.maxRetryAttempts());
    }

    @Test
    void functionName_empty_shouldThrow() {
        var exception = assertThrows(IllegalArgumentException.class, () -> DistributedMapProcessor.batch("")
                .build());
        assertEquals("function name cannot be empty", exception.getMessage());
    }

    @Test
    void functionName_null_shouldThrow() {
        var exception = assertThrows(IllegalArgumentException.class, () -> DistributedMapProcessor.batch(null)
                .build());
        assertEquals("function name cannot be empty", exception.getMessage());
    }

    @Test
    void functionName_tooLong_shouldThrow() {
        var exception =
                assertThrows(IllegalArgumentException.class, () -> DistributedMapProcessor.batch("a".repeat(171))
                        .build());
        assertEquals("function name must be at most 170 characters, got: 171", exception.getMessage());
    }

    @Test
    void functionName_atMaxLength_shouldPass() {
        var name = "a".repeat(170);
        assertEquals(name, DistributedMapProcessor.batch(name).build().functionName());
    }

    @Test
    void build_allFieldsSet() {
        var processor = DistributedMapProcessor.itemResults("fn")
                .batchSize(50)
                .durableExecutionNamePrefix("job")
                .maxRetryDuration(Duration.ofMinutes(5))
                .build();

        assertEquals("fn", processor.functionName());
        assertEquals(50, processor.batchSize());
        assertEquals("job", processor.durableExecutionNamePrefix());
        assertEquals(Duration.ofMinutes(5), processor.maxRetryDuration());
    }
}
