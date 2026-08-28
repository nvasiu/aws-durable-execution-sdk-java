// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.operation;

// Written against DistributedMap* shapes not yet in the generated Lambda client, so this does not compile until they ship.

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.services.lambda.model.DistributedMapCsvDelimiter;
import software.amazon.awssdk.services.lambda.model.DistributedMapCsvFormatOptions;
import software.amazon.awssdk.services.lambda.model.DistributedMapCsvHeaderLocation;
import software.amazon.awssdk.services.lambda.model.DistributedMapDestinationInclude;
import software.amazon.awssdk.services.lambda.model.DistributedMapDestinationType;
import software.amazon.awssdk.services.lambda.model.DistributedMapDetails;
import software.amazon.awssdk.services.lambda.model.DistributedMapFunctionResponseType;
import software.amazon.awssdk.services.lambda.model.DistributedMapInlineSourceConfig;
import software.amazon.awssdk.services.lambda.model.DistributedMapOnFailureConfig;
import software.amazon.awssdk.services.lambda.model.DistributedMapOnSuccessConfig;
import software.amazon.awssdk.services.lambda.model.DistributedMapOptions;
import software.amazon.awssdk.services.lambda.model.DistributedMapProcessorConfig;
import software.amazon.awssdk.services.lambda.model.DistributedMapReaderFunctionSourceConfig;
import software.amazon.awssdk.services.lambda.model.DistributedMapResultCollectionConfig;
import software.amazon.awssdk.services.lambda.model.DistributedMapResultCollectionMode;
import software.amazon.awssdk.services.lambda.model.DistributedMapS3DestinationConfig;
import software.amazon.awssdk.services.lambda.model.DistributedMapS3SourceConfig;
import software.amazon.awssdk.services.lambda.model.DistributedMapS3SourceTransform;
import software.amazon.awssdk.services.lambda.model.DistributedMapSourceConfig;
import software.amazon.awssdk.services.lambda.model.DistributedMapSourceFormat;
import software.amazon.awssdk.services.lambda.model.DistributedMapSourceType;
import software.amazon.lambda.durable.TypeToken;
import software.amazon.lambda.durable.config.CsvFormat;
import software.amazon.lambda.durable.config.DistributedMapConfig;
import software.amazon.lambda.durable.config.DistributedMapProcessor;
import software.amazon.lambda.durable.config.DistributedMapSource;
import software.amazon.lambda.durable.config.FailureDestination;
import software.amazon.lambda.durable.config.ProcessorRetryConfig;
import software.amazon.lambda.durable.config.SuccessDestination;
import software.amazon.lambda.durable.exception.DurableExecutionException;
import software.amazon.lambda.durable.model.DistributedMapCompletionReason;
import software.amazon.lambda.durable.model.DistributedMapItemError;
import software.amazon.lambda.durable.model.DistributedMapResultItem;
import software.amazon.lambda.durable.model.DistributedMapStatus;
import software.amazon.lambda.durable.model.DistributedMapSummary;
import software.amazon.lambda.durable.model.DistributedMapResult;
import software.amazon.lambda.durable.serde.JacksonSerDes;
import software.amazon.lambda.durable.serde.SerDes;

/** Translates distributed map config to the checkpoint options and the operation details to result types. */
public final class DistributedMapWire {
    private static final SerDes JSON = new JacksonSerDes();
    private static final long INLINE_SIZE_LIMIT = 1024L * 1024L;
    private static final long READER_STATE_LIMIT = 32L * 1024L;

    private DistributedMapWire() {}

    public static DistributedMapOptions toOptions(
            DistributedMapSource<?> source,
            DistributedMapProcessor processor,
            int maxConcurrency,
            DistributedMapConfig config,
            SerDes defaultSerDes,
            boolean collectResults) {
        var builder = DistributedMapOptions.builder()
                .maxConcurrency(maxConcurrency)
                .source(sourceConfig(source, defaultSerDes))
                .processor(processorConfig(processor));
        if (config.destination() != null) {
            var destination = destinationConfig(config.destination());
            if (destination != null) {
                builder.destination(destination);
            }
        }
        var completion = completionConfig(config);
        if (completion != null) {
            builder.completionConfig(completion);
        }
        if (collectResults) {
            builder.resultCollection(DistributedMapResultCollectionConfig.builder()
                    .mode(DistributedMapResultCollectionMode.INLINE)
                    .build());
        }
        if (config.timeout() != null) {
            builder.timeoutSeconds((int) config.timeout().toSeconds());
        }
        return builder.build();
    }

    private static DistributedMapSourceConfig sourceConfig(DistributedMapSource<?> source, SerDes defaultSerDes) {
        var builder = DistributedMapSourceConfig.builder()
                .type(DistributedMapSourceType.fromValue(source.sourceType().name()));
        if (source.maxItems() != null) {
            builder.maxItemsToRead(source.maxItems().longValue());
        }
        switch (source.sourceType()) {
            case INLINE -> builder.inlineSourceConfig(inlineSourceConfig(source, defaultSerDes));
            case S3 -> builder.s3SourceConfig(s3SourceConfig(source.s3()));
            case READER_FUNCTION -> builder.readerFunctionSourceConfig(readerConfig(source.reader(), defaultSerDes));
        }
        return builder.build();
    }

    private static DistributedMapInlineSourceConfig inlineSourceConfig(
            DistributedMapSource<?> source, SerDes defaultSerDes) {
        var serdes = source.inlineSerDes() != null ? source.inlineSerDes() : defaultSerDes;
        var bodies = new ArrayList<String>(source.inlineItems().size());
        for (Object item : source.inlineItems()) {
            bodies.add(serdes.serialize(item));
        }
        var arrayBytes = JSON.serialize(bodies).getBytes(StandardCharsets.UTF_8).length;
        if (arrayBytes > INLINE_SIZE_LIMIT) {
            throw new IllegalArgumentException(
                    "inline source exceeds the 1 MB limit (serialized size: " + arrayBytes + " bytes)");
        }
        return DistributedMapInlineSourceConfig.builder().items(bodies).build();
    }

    private static DistributedMapS3SourceConfig s3SourceConfig(DistributedMapSource.S3SourceConfig s3) {
        var builder = DistributedMapS3SourceConfig.builder().bucket(s3.bucket());
        if (s3.key() != null) {
            builder.key(s3.key());
        }
        if (s3.prefix() != null) {
            builder.keyPrefix(s3.prefix());
        }
        if (s3.transform() != null) {
            builder.transform(DistributedMapS3SourceTransform.fromValue(s3.transform().name()));
        }
        if (s3.format() != null) {
            builder.format(DistributedMapSourceFormat.fromValue(s3.format().name()));
        }
        if (s3.expectedBucketOwner() != null) {
            builder.expectedBucketOwner(s3.expectedBucketOwner());
        }
        if (s3.csvFormat() != null) {
            builder.csvFormatOptions(csvFormatOptions(s3.csvFormat()));
        }
        return builder.build();
    }

    private static DistributedMapCsvFormatOptions csvFormatOptions(CsvFormat format) {
        var builder = DistributedMapCsvFormatOptions.builder()
                .headerLocation(DistributedMapCsvHeaderLocation.fromValue(format.headerLocation().name()))
                .delimiter(DistributedMapCsvDelimiter.fromValue(format.delimiter().getValue()));
        // Column names go on the wire only for headerless files (GIVEN), expectedColumns (FIRST_ROW) is compile-time only.
        if (format.headerLocation() == CsvFormat.HeaderLocation.GIVEN && format.columns() != null) {
            builder.headers(format.columns());
        }
        return builder.build();
    }

    private static DistributedMapReaderFunctionSourceConfig readerConfig(
            DistributedMapSource.ReaderSourceConfig reader, SerDes defaultSerDes) {
        var builder = DistributedMapReaderFunctionSourceConfig.builder().functionName(reader.functionName());
        if (reader.initialState() != null) {
            var serdes = reader.stateSerDes() != null ? reader.stateSerDes() : defaultSerDes;
            var state = serdes.serialize(reader.initialState());
            if (state != null && state.getBytes(StandardCharsets.UTF_8).length > READER_STATE_LIMIT) {
                throw new IllegalArgumentException("reader initialState exceeds the 32 KB limit");
            }
            builder.initialState(state);
        }
        return builder.build();
    }

    private static DistributedMapProcessorConfig processorConfig(DistributedMapProcessor processor) {
        var builder = DistributedMapProcessorConfig.builder().functionName(processor.functionName());
        if (processor.durableExecutionNamePrefix() != null) {
            builder.durableExecutionNamePrefix(processor.durableExecutionNamePrefix());
        }
        var responseType = processor.responseMode().getValue();
        if (responseType != null) {
            builder.functionResponseTypes(List.of(DistributedMapFunctionResponseType.fromValue(responseType)));
        }
        if (processor.batchSize() != null) {
            builder.batchSize(processor.batchSize());
        }
        ProcessorRetryConfig retry = processor.retryConfig();
        if (retry != null) {
            if (retry.maxRetryAttempts() != null) {
                builder.maxRetryAttempts(retry.maxRetryAttempts());
            }
            if (retry.maxRetryDuration() != null) {
                builder.maxRetryDurationSeconds((int) retry.maxRetryDuration().toSeconds());
            }
        }
        return builder.build();
    }

    private static software.amazon.awssdk.services.lambda.model.DistributedMapDestinationConfig destinationConfig(
            software.amazon.lambda.durable.config.DistributedMapDestinationConfig destination) {
        if (destination.onSuccess() == null && destination.onFailure() == null) {
            return null; // omit an empty destination config from the wire
        }
        var builder = software.amazon.awssdk.services.lambda.model.DistributedMapDestinationConfig.builder();
        SuccessDestination onSuccess = destination.onSuccess();
        if (onSuccess != null) {
            var include = new ArrayList<DistributedMapDestinationInclude>();
            if (onSuccess.includeInput()) {
                include.add(DistributedMapDestinationInclude.INPUT);
            }
            if (onSuccess.includeOutput()) {
                include.add(DistributedMapDestinationInclude.OUTPUT);
            }
            builder.onSuccess(DistributedMapOnSuccessConfig.builder()
                    .type(DistributedMapDestinationType.S3)
                    .include(include)
                    .s3DestinationConfig(s3Destination(
                            onSuccess.bucket(), onSuccess.prefix(), onSuccess.expectedBucketOwner()))
                    .build());
        }
        FailureDestination onFailure = destination.onFailure();
        if (onFailure != null) {
            var include = new ArrayList<DistributedMapDestinationInclude>();
            if (onFailure.includeInput()) {
                include.add(DistributedMapDestinationInclude.INPUT);
            }
            if (onFailure.includeError()) {
                include.add(DistributedMapDestinationInclude.ERROR);
            }
            builder.onFailure(DistributedMapOnFailureConfig.builder()
                    .type(DistributedMapDestinationType.S3)
                    .include(include)
                    .s3DestinationConfig(s3Destination(
                            onFailure.bucket(), onFailure.prefix(), onFailure.expectedBucketOwner()))
                    .build());
        }
        return builder.build();
    }

    private static DistributedMapS3DestinationConfig s3Destination(
            String bucket, String keyPrefix, String expectedBucketOwner) {
        var builder = DistributedMapS3DestinationConfig.builder().bucket(bucket).keyPrefix(keyPrefix);
        if (expectedBucketOwner != null) {
            builder.expectedBucketOwner(expectedBucketOwner);
        }
        return builder.build();
    }

    private static software.amazon.awssdk.services.lambda.model.DistributedMapCompletionConfig completionConfig(
            DistributedMapConfig config) {
        var completion = config.completionConfig();
        if (completion == null
                || (completion.toleratedFailureCount() == null
                        && completion.toleratedFailurePercentage() == null
                        && completion.minimumSampleSize() == null)) {
            return null; // omit an empty completion config from the wire
        }
        var builder = software.amazon.awssdk.services.lambda.model.DistributedMapCompletionConfig.builder();
        if (completion.toleratedFailureCount() != null) {
            builder.toleratedFailureCount(completion.toleratedFailureCount());
        }
        if (completion.toleratedFailurePercentage() != null) {
            builder.toleratedFailurePercentage(completion.toleratedFailurePercentage().floatValue());
        }
        if (completion.minimumSampleSize() != null) {
            builder.minimumSampleSize(completion.minimumSampleSize());
        }
        return builder.build();
    }

    public static DistributedMapSummary toSummary(DistributedMapDetails details) {
        return new DistributedMapSummary(
                parseStatus(details),
                parseReason(details),
                details.successCount(),
                details.failureCount(),
                details.unprocessedCount(),
                details.distributedMapRunArn(),
                details.completionDetails(),
                details.totalCount());
    }

    public static <O> DistributedMapResult<O> toResult(
            DistributedMapDetails details, TypeToken<O> resultType, SerDes serdes) {
        var summary = toSummary(details);
        var items = new ArrayList<DistributedMapResultItem<O>>();
        if (details.results() != null) {
            for (var wire : details.results()) {
                var status = wire.statusAsString();
                if (DistributedMapResultItem.Status.SUCCEEDED.name().equals(status)) {
                    O output = wire.output() != null ? serdes.deserialize(wire.output(), resultType) : null;
                    items.add(DistributedMapResultItem.succeeded(wire.itemId(), output));
                } else if (DistributedMapResultItem.Status.FAILED.name().equals(status)) {
                    var error = wire.error() != null
                            ? new DistributedMapItemError(
                                    wire.error().errorType() != null ? wire.error().errorType() : "",
                                    wire.error().errorMessage() != null ? wire.error().errorMessage() : "")
                            : null;
                    items.add(DistributedMapResultItem.failed(wire.itemId(), error));
                } else {
                    throw new DurableExecutionException("unrecognized distributed map item status from the backend ("
                            + status + ") for item " + wire.itemId());
                }
            }
        }
        return new DistributedMapResult<>(summary, items);
    }

    private static DistributedMapStatus parseStatus(DistributedMapDetails details) {
        if (details.statusAsString() == null) {
            throw new DurableExecutionException("distributed map details missing the required Status field");
        }
        try {
            return DistributedMapStatus.fromValue(details.statusAsString());
        } catch (IllegalArgumentException e) {
            throw new DurableExecutionException(
                    "unrecognized distributed map status from the backend (" + details.statusAsString() + ")", e);
        }
    }

    private static DistributedMapCompletionReason parseReason(DistributedMapDetails details) {
        if (details.completionReasonAsString() == null) {
            throw new DurableExecutionException("distributed map details missing the required CompletionReason field");
        }
        return DistributedMapCompletionReason.fromValue(details.completionReasonAsString());
    }
}
