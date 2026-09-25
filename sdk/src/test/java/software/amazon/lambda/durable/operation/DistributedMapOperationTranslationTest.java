// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.operation;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.lambda.model.DistributedMapDetails;
import software.amazon.awssdk.services.lambda.model.DistributedMapOptions;
import software.amazon.awssdk.services.lambda.model.DistributedMapResultItem;
import software.amazon.awssdk.services.lambda.model.ErrorObject;
import software.amazon.lambda.durable.TypeToken;
import software.amazon.lambda.durable.config.DistributedMapConfig;
import software.amazon.lambda.durable.config.DistributedMapDestination;
import software.amazon.lambda.durable.config.DistributedMapProcessor;
import software.amazon.lambda.durable.config.DistributedMapSource;
import software.amazon.lambda.durable.exception.DurableExecutionException;
import software.amazon.lambda.durable.model.DistributedMapCompletionReason;
import software.amazon.lambda.durable.model.DistributedMapStatus;
import software.amazon.lambda.durable.serde.JacksonSerDes;
import software.amazon.lambda.durable.serde.SerDes;

/** Translation tests for DistributedMapOperation toOptions, toSummary and toResult. */
class DistributedMapOperationTranslationTest {

    private static final SerDes SERDES = new JacksonSerDes();

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static DistributedMapOptions options(
            DistributedMapSource<?> source,
            DistributedMapProcessor processor,
            DistributedMapConfig config,
            boolean collect) {
        return DistributedMapOperation.toOptions(source, processor, 4, config, SERDES, collect);
    }

    private static DistributedMapOptions options(DistributedMapSource<?> source, DistributedMapProcessor processor) {
        return options(source, processor, DistributedMapConfig.builder().build(), false);
    }

    private static DistributedMapProcessor batchProcessor() {
        return DistributedMapProcessor.batch("proc").build();
    }

    private static DistributedMapDetails.Builder detailsBuilder() {
        return DistributedMapDetails.builder()
                .status("SUCCEEDED")
                .completionReason("ALL_COMPLETED")
                .successCount(1L)
                .failureCount(0L)
                .unprocessedCount(0L);
    }

    private static DistributedMapResultItem succeededItem(String itemId, String output) {
        return DistributedMapResultItem.builder()
                .itemId(itemId)
                .status("SUCCEEDED")
                .output(output)
                .build();
    }

    private static DistributedMapResultItem failedItem(String itemId, ErrorObject error) {
        return DistributedMapResultItem.builder()
                .itemId(itemId)
                .status("FAILED")
                .error(error)
                .build();
    }

    /** Serdes that upper-cases on serialize and upper-cases plus counts on deserialize. */
    private static final class UpperSerDes implements SerDes {
        int deserializeCount = 0;

        @Override
        public String serialize(Object value) {
            return String.valueOf(value).toUpperCase(Locale.ROOT);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T deserialize(String data, TypeToken<T> typeToken) {
            deserializeCount++;
            return (T) data.toUpperCase(Locale.ROOT);
        }
    }

    // ------------------------------------------------------------------
    // toOptions - top level
    // ------------------------------------------------------------------

    @Test
    void inlineSourceSerializesItemsThroughDefaultSerdes() {
        var options = options(DistributedMapSource.inline(List.of("a", "b")), batchProcessor());
        assertEquals(4, options.maxConcurrency().intValue());
        assertEquals("INLINE", options.source().typeAsString());
        assertEquals(
                List.of(SERDES.serialize("a"), SERDES.serialize("b")),
                options.source().inlineSourceConfig().items());
    }

    @Test
    void inlineSourceUsesCustomSerdesForEachItem() {
        var options = options(DistributedMapSource.inline(List.of("a", "b"), new UpperSerDes()), batchProcessor());
        assertEquals(List.of("A", "B"), options.source().inlineSourceConfig().items());
    }

    @Test
    void maxConcurrencyPassedThrough() {
        var options = DistributedMapOperation.toOptions(
                DistributedMapSource.inline(List.of("a")),
                batchProcessor(),
                42,
                DistributedMapConfig.builder().build(),
                SERDES,
                false);
        assertEquals(42, options.maxConcurrency().intValue());
    }

    @Test
    void timeoutMapsToWholeSeconds() {
        var config =
                DistributedMapConfig.builder().timeout(Duration.ofMinutes(30)).build();
        var options = options(DistributedMapSource.inline(List.of("a")), batchProcessor(), config, false);
        assertEquals(1800, options.timeoutSeconds().intValue());
    }

    @Test
    void timeoutOmittedWhenAbsent() {
        var options = options(DistributedMapSource.inline(List.of("a")), batchProcessor());
        assertNull(options.timeoutSeconds());
    }

    // ------------------------------------------------------------------
    // toOptions - processor
    // ------------------------------------------------------------------

    @Test
    void itemFailuresSetsResponseTypeAndBatchSize() {
        var processor =
                DistributedMapProcessor.itemFailures("proc").batchSize(25).build();
        var wire = options(DistributedMapSource.inline(List.of("a")), processor).processor();
        assertEquals("proc", wire.functionName());
        assertEquals(List.of("REPORT_BATCH_ITEM_FAILURES"), wire.functionResponseTypesAsStrings());
        assertEquals(25, wire.batchSize().intValue());
    }

    @Test
    void itemResultsSetsResponseType() {
        var processor = DistributedMapProcessor.itemResults("proc").build();
        var wire = options(DistributedMapSource.inline(List.of("a")), processor).processor();
        assertEquals(List.of("REPORT_BATCH_ITEM_RESULTS"), wire.functionResponseTypesAsStrings());
    }

    @Test
    void batchOmitsResponseTypes() {
        var wire = options(DistributedMapSource.inline(List.of("a")), batchProcessor())
                .processor();
        assertFalse(wire.hasFunctionResponseTypes());
    }

    @Test
    void unlimitedRetriesMapToNegativeOne() {
        var processor = DistributedMapProcessor.itemResults("proc")
                .maxRetryAttempts(DistributedMapProcessor.UNLIMITED)
                .maxRetryDuration(Duration.ofHours(1))
                .build();
        var wire = options(DistributedMapSource.inline(List.of("a")), processor).processor();
        assertEquals(-1, wire.maxRetryAttempts().intValue());
        assertEquals(3600, wire.maxRetryDurationSeconds().intValue());
    }

    @Test
    void explicitRetryAttemptsPassThrough() {
        var processor =
                DistributedMapProcessor.batch("proc").maxRetryAttempts(0).build();
        var wire = options(DistributedMapSource.inline(List.of("a")), processor).processor();
        assertEquals(0, wire.maxRetryAttempts().intValue());
    }

    @Test
    void retryDurationOnlyOmitsAttempts() {
        var processor = DistributedMapProcessor.batch("proc")
                .maxRetryDuration(Duration.ofMinutes(5))
                .build();
        var wire = options(DistributedMapSource.inline(List.of("a")), processor).processor();
        assertEquals(300, wire.maxRetryDurationSeconds().intValue());
        assertNull(wire.maxRetryAttempts());
    }

    @Test
    void durableExecutionNamePrefixSent() {
        var processor = DistributedMapProcessor.batch("proc")
                .durableExecutionNamePrefix("pfx")
                .build();
        var wire = options(DistributedMapSource.inline(List.of("a")), processor).processor();
        assertEquals("pfx", wire.durableExecutionNamePrefix());
    }

    // ------------------------------------------------------------------
    // toOptions - source variants
    // ------------------------------------------------------------------

    @Test
    void s3JsonLinesSourceSerializesConfig() {
        var source = DistributedMapSource.s3JsonLines("s3://bucket/data.jsonl").maxItemsToRead(500);
        var wire = options(source, batchProcessor()).source();
        assertEquals("S3", wire.typeAsString());
        assertEquals(500L, wire.maxItemsToRead().longValue());
        assertEquals("bucket", wire.s3SourceConfig().bucket());
        assertEquals("data.jsonl", wire.s3SourceConfig().key());
        assertEquals("JSON_LINES", wire.s3SourceConfig().formatAsString());
    }

    @Test
    void s3ObjectsSourceUsesNoneTransformAndPrefix() {
        var wire = options(DistributedMapSource.s3Objects("s3://b/prefix/"), batchProcessor())
                .source();
        var s3 = wire.s3SourceConfig();
        assertEquals("NONE", s3.transformAsString());
        assertEquals("prefix/", s3.keyPrefix());
        assertNull(s3.formatAsString());
        assertNull(s3.key());
    }

    @Test
    void s3FlattenedJsonLinesUsesLoadAndFlatten() {
        var wire = options(DistributedMapSource.s3FlattenedJsonLines("s3://b/prefix/"), batchProcessor())
                .source();
        assertEquals("LOAD_AND_FLATTEN", wire.s3SourceConfig().transformAsString());
        assertEquals("JSON_LINES", wire.s3SourceConfig().formatAsString());
    }

    @Test
    void s3FlattenedCsvUsesLoadAndFlattenAndCsvOptions() {
        var source = DistributedMapSource.s3FlattenedCsv(
                "s3://b/prefix", DistributedMapSource.CsvFormat.headers(List.of("a", "b")));
        var s3 = options(source, batchProcessor()).source().s3SourceConfig();
        assertEquals("LOAD_AND_FLATTEN", s3.transformAsString());
        assertEquals("CSV", s3.formatAsString());
        assertEquals("GIVEN", s3.csvFormatOptions().headerLocationAsString());
        assertEquals(List.of("a", "b"), s3.csvFormatOptions().headers());
    }

    @Test
    void s3ExpectedBucketOwnerSent() {
        var source = DistributedMapSource.s3JsonLines("s3://b/k.jsonl").expectedBucketOwner("123456789012");
        var s3 = options(source, batchProcessor()).source().s3SourceConfig();
        assertEquals("123456789012", s3.expectedBucketOwner());
    }

    @Test
    void readerSourceSerializesInitialState() {
        var source = DistributedMapSource.reader("reader", Map.of("page", 0), null);
        var reader = options(source, batchProcessor()).source().readerFunctionSourceConfig();
        assertEquals("reader", reader.functionName());
        assertEquals(SERDES.serialize(Map.of("page", 0)), reader.initialState());
    }

    @Test
    void readerSourceWithoutInitialStateOmitsState() {
        var reader = options(DistributedMapSource.reader("reader"), batchProcessor())
                .source()
                .readerFunctionSourceConfig();
        assertEquals("reader", reader.functionName());
        assertNull(reader.initialState());
    }

    // ------------------------------------------------------------------
    // toOptions - CSV header handling
    // ------------------------------------------------------------------

    @Test
    void csvHeadersMapToGivenAndAreSent() {
        var source = DistributedMapSource.s3Csv(
                "s3://b/data.csv", DistributedMapSource.CsvFormat.headers(List.of("a", "b")));
        var csv = options(source, batchProcessor()).source().s3SourceConfig().csvFormatOptions();
        assertEquals("GIVEN", csv.headerLocationAsString());
        assertEquals(List.of("a", "b"), csv.headers());
        assertEquals("COMMA", csv.delimiterAsString());
    }

    @Test
    void csvFirstRowOmitsHeaders() {
        var source = DistributedMapSource.s3Csv("s3://b/data.csv", DistributedMapSource.CsvFormat.firstRow());
        var csv = options(source, batchProcessor()).source().s3SourceConfig().csvFormatOptions();
        assertEquals("FIRST_ROW", csv.headerLocationAsString());
        assertFalse(csv.hasHeaders());
        assertEquals("COMMA", csv.delimiterAsString());
    }

    @Test
    void csvExpectedColumnsStayClientSide() {
        var source = DistributedMapSource.s3Csv(
                "s3://b/data.csv", DistributedMapSource.CsvFormat.expectedColumns(List.of("a", "b")));
        var csv = options(source, batchProcessor()).source().s3SourceConfig().csvFormatOptions();
        assertEquals("FIRST_ROW", csv.headerLocationAsString());
        assertFalse(csv.hasHeaders());
    }

    @Test
    void csvDelimiterMapsToWireValue() {
        var source = DistributedMapSource.s3Csv(
                "s3://b/data.csv",
                DistributedMapSource.CsvFormat.headers(List.of("a"))
                        .withDelimiter(DistributedMapSource.CsvDelimiter.PIPE));
        var csv = options(source, batchProcessor()).source().s3SourceConfig().csvFormatOptions();
        assertEquals("PIPE", csv.delimiterAsString());
    }

    // ------------------------------------------------------------------
    // toOptions - completion config
    // ------------------------------------------------------------------

    @Test
    void completionPercentageAndSampleSizeSerialize() {
        var config = DistributedMapConfig.builder()
                .completionConfig(DistributedMapConfig.CompletionConfig.failurePercentage(5, 200))
                .build();
        var completion = options(DistributedMapSource.inline(List.of("a")), batchProcessor(), config, false)
                .completionConfig();
        assertEquals(5.0f, completion.toleratedFailurePercentage(), 0.0001f);
        assertEquals(200, completion.minimumSampleSize().intValue());
        assertNull(completion.toleratedFailureCount());
    }

    @Test
    void completionFailureCountSerializes() {
        var config = DistributedMapConfig.builder()
                .completionConfig(DistributedMapConfig.CompletionConfig.failureCount(2))
                .build();
        var completion = options(DistributedMapSource.inline(List.of("a")), batchProcessor(), config, false)
                .completionConfig();
        assertEquals(2, completion.toleratedFailureCount().intValue());
    }

    @Test
    void emptyCompletionConfigOmittedFromWire() {
        var config = DistributedMapConfig.builder()
                .completionConfig(new DistributedMapConfig.CompletionConfig(null, null, null))
                .build();
        var options = options(DistributedMapSource.inline(List.of("a")), batchProcessor(), config, false);
        assertNull(options.completionConfig());
    }

    // ------------------------------------------------------------------
    // toOptions - destinations
    // ------------------------------------------------------------------

    @Test
    void destinationBothSuccessAndFailureSerialize() {
        var config = DistributedMapConfig.builder()
                .destination(DistributedMapDestination.of(
                        new DistributedMapDestination.Success("out", "ok", false, true, null),
                        new DistributedMapDestination.Failure("out", "bad", true, true, null)))
                .build();
        var destination = options(DistributedMapSource.inline(List.of("a")), batchProcessor(), config, false)
                .destination();
        var onSuccess = destination.onSuccess();
        assertEquals("S3", onSuccess.typeAsString());
        assertEquals(List.of("OUTPUT"), onSuccess.includeAsStrings());
        assertEquals("out", onSuccess.s3DestinationConfig().bucket());
        assertEquals("ok", onSuccess.s3DestinationConfig().keyPrefix());
        var onFailure = destination.onFailure();
        assertEquals(List.of("INPUT", "ERROR"), onFailure.includeAsStrings());
        assertEquals("bad", onFailure.s3DestinationConfig().keyPrefix());
    }

    @Test
    void destinationOnlySuccessOmitsFailure() {
        var config = DistributedMapConfig.builder()
                .destination(DistributedMapDestination.of(
                        new DistributedMapDestination.Success("out", "ok", false, true, null)))
                .build();
        var destination = options(DistributedMapSource.inline(List.of("a")), batchProcessor(), config, false)
                .destination();
        assertNotNull(destination.onSuccess());
        assertNull(destination.onFailure());
    }

    @Test
    void destinationOnlyFailureOmitsSuccess() {
        var config = DistributedMapConfig.builder()
                .destination(DistributedMapDestination.of(
                        new DistributedMapDestination.Failure("out", "bad", false, true, null)))
                .build();
        var destination = options(DistributedMapSource.inline(List.of("a")), batchProcessor(), config, false)
                .destination();
        assertNotNull(destination.onFailure());
        assertNull(destination.onSuccess());
    }

    @Test
    void allNullDestinationOmittedFromWire() {
        var config = DistributedMapConfig.builder()
                .destination(new DistributedMapDestination(null, null))
                .build();
        var options = options(DistributedMapSource.inline(List.of("a")), batchProcessor(), config, false);
        assertNull(options.destination());
    }

    @Test
    void successDestinationIncludeInputAndOutputWithOwner() {
        var config = DistributedMapConfig.builder()
                .destination(DistributedMapDestination.of(
                        new DistributedMapDestination.Success("out", "ok", true, true, "123456789012")))
                .build();
        var onSuccess = options(DistributedMapSource.inline(List.of("a")), batchProcessor(), config, false)
                .destination()
                .onSuccess();
        assertEquals(List.of("INPUT", "OUTPUT"), onSuccess.includeAsStrings());
        assertEquals("123456789012", onSuccess.s3DestinationConfig().expectedBucketOwner());
    }

    @Test
    void successDestinationInputOnly() {
        var config = DistributedMapConfig.builder()
                .destination(DistributedMapDestination.of(
                        new DistributedMapDestination.Success("out", "ok", true, false, null)))
                .build();
        var onSuccess = options(DistributedMapSource.inline(List.of("a")), batchProcessor(), config, false)
                .destination()
                .onSuccess();
        assertEquals(List.of("INPUT"), onSuccess.includeAsStrings());
    }

    @Test
    void failureDestinationErrorOnly() {
        var config = DistributedMapConfig.builder()
                .destination(DistributedMapDestination.of(
                        new DistributedMapDestination.Failure("out", "bad", false, true, "123456789012")))
                .build();
        var onFailure = options(DistributedMapSource.inline(List.of("a")), batchProcessor(), config, false)
                .destination()
                .onFailure();
        assertEquals(List.of("ERROR"), onFailure.includeAsStrings());
        assertEquals("123456789012", onFailure.s3DestinationConfig().expectedBucketOwner());
    }

    @Test
    void failureDestinationInputOnly() {
        var config = DistributedMapConfig.builder()
                .destination(DistributedMapDestination.of(
                        new DistributedMapDestination.Failure("out", "bad", true, false, null)))
                .build();
        var onFailure = options(DistributedMapSource.inline(List.of("a")), batchProcessor(), config, false)
                .destination()
                .onFailure();
        assertEquals(List.of("INPUT"), onFailure.includeAsStrings());
    }

    // ------------------------------------------------------------------
    // toOptions - result collection
    // ------------------------------------------------------------------

    @Test
    void resultCollectionInlineWhenCollecting() {
        var options = options(
                DistributedMapSource.inline(List.of("a")),
                batchProcessor(),
                DistributedMapConfig.builder().build(),
                true);
        assertEquals("INLINE", options.resultCollection().modeAsString());
    }

    @Test
    void resultCollectionOmittedWhenNotCollecting() {
        var options = options(DistributedMapSource.inline(List.of("a")), batchProcessor());
        assertNull(options.resultCollection());
    }

    // ------------------------------------------------------------------
    // toSummary
    // ------------------------------------------------------------------

    @Test
    void toSummaryMapsDetailFields() {
        var details = detailsBuilder()
                .successCount(5L)
                .totalCount(5L)
                .distributedMapRunArn("arn:aws:lambda:us-east-1:123456789012:map-run:abc")
                .build();
        var summary = DistributedMapOperation.toSummary(DistributedMapStatus.SUCCEEDED, details);
        assertEquals(DistributedMapStatus.SUCCEEDED, summary.status());
        assertEquals(DistributedMapCompletionReason.ALL_COMPLETED, summary.completionReason());
        assertEquals(5L, summary.successCount());
        assertEquals(5L, summary.totalCount().longValue());
        assertEquals("arn:aws:lambda:us-east-1:123456789012:map-run:abc", summary.distributedMapRunArn());
    }

    @Test
    void toSummaryUnknownCompletionReasonMapsToSentinel() {
        var details = detailsBuilder().completionReason("BRAND_NEW_REASON").build();
        assertEquals(
                DistributedMapCompletionReason.UNKNOWN_TO_SDK_VERSION,
                DistributedMapOperation.toSummary(DistributedMapStatus.SUCCEEDED, details)
                        .completionReason());
    }

    @Test
    void toSummaryUsesOperationStatusAndIgnoresDetailsStatus() {
        // The backend no longer sends Status in the details, so the operation-derived status is authoritative
        // even when a details Status is present and disagrees.
        var details = detailsBuilder().status("SUCCEEDED").build();
        assertEquals(
                DistributedMapStatus.FAILED,
                DistributedMapOperation.toSummary(DistributedMapStatus.FAILED, details)
                        .status());
    }

    @Test
    void toSummaryMissingStatusUsesOperationStatus() {
        var details = DistributedMapDetails.builder()
                .completionReason("ALL_COMPLETED")
                .successCount(1L)
                .failureCount(0L)
                .unprocessedCount(0L)
                .build();
        assertEquals(
                DistributedMapStatus.STOPPED,
                DistributedMapOperation.toSummary(DistributedMapStatus.STOPPED, details)
                        .status());
    }

    @Test
    void toSummaryMissingCompletionReasonIsNull() {
        var details = DistributedMapDetails.builder()
                .status("SUCCEEDED")
                .successCount(1L)
                .failureCount(0L)
                .unprocessedCount(0L)
                .build();
        assertNull(DistributedMapOperation.toSummary(DistributedMapStatus.SUCCEEDED, details)
                .completionReason());
    }

    // ------------------------------------------------------------------
    // toResult
    // ------------------------------------------------------------------

    @Test
    void toResultParsesSucceededAndFailedItems() {
        var details = detailsBuilder()
                .failureCount(1L)
                .results(List.of(
                        succeededItem("0", "42"),
                        failedItem(
                                "1",
                                ErrorObject.builder()
                                        .errorType("E")
                                        .errorMessage("boom")
                                        .build())))
                .build();
        var result = DistributedMapOperation.toResult(
                DistributedMapStatus.SUCCEEDED, details, TypeToken.get(Integer.class), SERDES);
        assertEquals(List.of(42), result.getResults());
        assertEquals(1, result.getErrors().size());
        assertEquals("E", result.getErrors().get(0).errorType());
        assertEquals("boom", result.getErrors().get(0).errorMessage());
    }

    @Test
    void toResultDecodesOutputExactlyOnceThroughSerdes() {
        var serdes = new UpperSerDes();
        var details =
                detailsBuilder().results(List.of(succeededItem("0", "abc"))).build();
        var result = DistributedMapOperation.toResult(
                DistributedMapStatus.SUCCEEDED, details, TypeToken.get(String.class), serdes);
        assertEquals(List.of("ABC"), result.getResults());
        assertEquals(1, serdes.deserializeCount);
    }

    @Test
    void toResultKeepsNullOutputNull() {
        var details =
                detailsBuilder().results(List.of(succeededItem("0", null))).build();
        var result = DistributedMapOperation.toResult(
                DistributedMapStatus.SUCCEEDED, details, TypeToken.get(String.class), SERDES);
        assertNull(result.succeeded().get(0).output());
        assertEquals(1, result.getResults().size());
        assertNull(result.getResults().get(0));
    }

    @Test
    void toResultCoercesNullItemErrorFieldsToEmptyString() {
        var details = detailsBuilder()
                .failureCount(1L)
                .results(List.of(failedItem("0", ErrorObject.builder().build())))
                .build();
        var error = DistributedMapOperation.toResult(
                        DistributedMapStatus.SUCCEEDED, details, TypeToken.get(String.class), SERDES)
                .getErrors()
                .get(0);
        assertEquals("", error.errorType());
        assertEquals("", error.errorMessage());
    }

    @Test
    void toResultKeepsFailedItemErrorNullWhenAbsent() {
        var details = detailsBuilder()
                .failureCount(1L)
                .results(List.of(failedItem("0", null)))
                .build();
        var result = DistributedMapOperation.toResult(
                DistributedMapStatus.SUCCEEDED, details, TypeToken.get(String.class), SERDES);
        assertNull(result.failed().get(0).error());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void toResultThrowsOnUnknownItemStatus() {
        var details = detailsBuilder()
                .results(List.of(DistributedMapResultItem.builder()
                        .itemId("0")
                        .status("BOGUS")
                        .build()))
                .build();
        assertThrows(
                DurableExecutionException.class,
                () -> DistributedMapOperation.toResult(
                        DistributedMapStatus.SUCCEEDED, details, TypeToken.get(String.class), SERDES));
    }
}
