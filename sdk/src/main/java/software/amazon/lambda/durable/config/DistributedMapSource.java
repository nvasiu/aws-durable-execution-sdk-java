// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.config;

import java.util.Collection;
import java.util.List;
import software.amazon.lambda.durable.serde.SerDes;
import software.amazon.lambda.durable.util.DistributedMapValidation;
import software.amazon.lambda.durable.util.ParameterValidator;

/**
 * Source of items for a distributed map run.
 *
 * @param <I> the item type produced by the source
 */
public class DistributedMapSource<I> {

    /** The kind of source backing a distributed map. */
    public enum SourceType {
        INLINE,
        S3,
        READER_FUNCTION
    }

    /** How the bytes of an S3 object are split into items. */
    public enum Format {
        JSON_LINES,
        JSON_ARRAY,
        CSV
    }

    /** Whether the backend flattens object contents into items or treats each object as one item. */
    public enum Transform {
        NONE,
        LOAD_AND_FLATTEN
    }

    private final SourceType sourceType;
    private final Long maxItems;
    private final List<I> inlineItems;
    private final SerDes inlineSerDes;
    private final S3SourceConfig s3;
    private final ReaderSourceConfig reader;

    private DistributedMapSource(
            SourceType sourceType,
            Long maxItems,
            List<I> inlineItems,
            SerDes inlineSerDes,
            S3SourceConfig s3,
            ReaderSourceConfig reader) {
        if (maxItems != null && maxItems < 1) {
            throw new IllegalArgumentException("maxItems must be at least 1, got: " + maxItems);
        }
        this.sourceType = sourceType;
        this.maxItems = maxItems;
        this.inlineItems = inlineItems != null ? List.copyOf(inlineItems) : null;
        this.inlineSerDes = inlineSerDes;
        this.s3 = s3;
        this.reader = reader;
    }

    public SourceType sourceType() {
        return sourceType;
    }

    public Long maxItems() {
        return maxItems;
    }

    public List<I> inlineItems() {
        return inlineItems;
    }

    public SerDes inlineSerDes() {
        return inlineSerDes;
    }

    public S3SourceConfig s3() {
        return s3;
    }

    public ReaderSourceConfig reader() {
        return reader;
    }

    /** An in-memory list of items embedded in the start checkpoint. */
    public static <I> DistributedMapSource<I> inline(Collection<I> items) {
        return inline(items, null);
    }

    /** An in-memory list of items embedded in the start checkpoint, serialized with the given SerDes. */
    public static <I> DistributedMapSource<I> inline(Collection<I> items, SerDes serDes) {
        ParameterValidator.validateOrderedCollection(items);
        return new DistributedMapSource<>(SourceType.INLINE, null, List.copyOf(items), serDes, null, null);
    }

    /** Read a single S3 object, treating each line as an item. */
    public static <I> DistributedMapSource<I> s3JsonLines(String uri) {
        var parsed = DistributedMapValidation.parseS3Uri(uri);
        if (parsed.path() == null) {
            throw new IllegalArgumentException("s3JsonLines requires an S3 object key");
        }
        return s3(new S3SourceConfig(parsed.bucket(), parsed.path(), null, null, Format.JSON_LINES, null, null));
    }

    /** Read a single S3 object holding a JSON array, treating each element as an item. */
    public static <I> DistributedMapSource<I> s3JsonArray(String uri) {
        var parsed = DistributedMapValidation.parseS3Uri(uri);
        if (parsed.path() == null) {
            throw new IllegalArgumentException("s3JsonArray requires an S3 object key");
        }
        return s3(new S3SourceConfig(parsed.bucket(), parsed.path(), null, null, Format.JSON_ARRAY, null, null));
    }

    /** Read a single S3 object, treating each record as an item. */
    public static <I> DistributedMapSource<I> s3Csv(String uri, CsvFormat format) {
        var parsed = DistributedMapValidation.parseS3Uri(uri);
        if (parsed.path() == null) {
            throw new IllegalArgumentException("s3Csv requires an S3 object key");
        }
        return s3(new S3SourceConfig(parsed.bucket(), parsed.path(), null, null, Format.CSV, format, null));
    }

    /** Read each object under a prefix as one item (object contents are not read). */
    public static <I> DistributedMapSource<I> s3Objects(String prefixUri) {
        var parsed = DistributedMapValidation.parseS3Uri(prefixUri);
        return s3(new S3SourceConfig(parsed.bucket(), null, prefixOrEmpty(parsed.path()), Transform.NONE, null, null, null));
    }

    /** Read a prefix, flattening each object's lines into items. */
    public static <I> DistributedMapSource<I> s3FlattenedJsonLines(String prefixUri) {
        var parsed = DistributedMapValidation.parseS3Uri(prefixUri);
        return s3(new S3SourceConfig(
                parsed.bucket(), null, prefixOrEmpty(parsed.path()), Transform.LOAD_AND_FLATTEN, Format.JSON_LINES, null, null));
    }

    /** Read a prefix, flattening each object's JSON array elements into items. */
    public static <I> DistributedMapSource<I> s3FlattenedJsonArray(String prefixUri) {
        var parsed = DistributedMapValidation.parseS3Uri(prefixUri);
        return s3(new S3SourceConfig(
                parsed.bucket(), null, prefixOrEmpty(parsed.path()), Transform.LOAD_AND_FLATTEN, Format.JSON_ARRAY, null, null));
    }

    /** Read a prefix, flattening each object's records into items. */
    public static <I> DistributedMapSource<I> s3FlattenedCsv(String prefixUri, CsvFormat format) {
        var parsed = DistributedMapValidation.parseS3Uri(prefixUri);
        return s3(new S3SourceConfig(
                parsed.bucket(), null, prefixOrEmpty(parsed.path()), Transform.LOAD_AND_FLATTEN, Format.CSV, format, null));
    }

    /** Page items from a customer-supplied reader Lambda function. */
    public static <I> DistributedMapSource<I> reader(String functionName) {
        return reader(functionName, null, null);
    }

    /** Page items from a customer-supplied reader Lambda function with an initial state. */
    public static <I, S> DistributedMapSource<I> reader(String functionName, S initialState, SerDes stateSerDes) {
        return new DistributedMapSource<>(
                SourceType.READER_FUNCTION,
                null,
                null,
                null,
                null,
                new ReaderSourceConfig(functionName, initialState, stateSerDes));
    }

    /** Returns a copy limiting the total number of items read. */
    public DistributedMapSource<I> maxItemsToRead(long maxItems) {
        return new DistributedMapSource<>(sourceType, maxItems, inlineItems, inlineSerDes, s3, reader);
    }

    /** Returns a copy asserting the expected S3 bucket owner (S3 sources only). */
    public DistributedMapSource<I> expectedBucketOwner(String accountId) {
        if (s3 == null) {
            throw new IllegalStateException("expectedBucketOwner only applies to S3 sources");
        }
        DistributedMapValidation.validateBucketOwner(accountId);
        return new DistributedMapSource<>(
                sourceType, maxItems, inlineItems, inlineSerDes, s3.withExpectedBucketOwner(accountId), reader);
    }

    private static <I> DistributedMapSource<I> s3(S3SourceConfig s3) {
        return new DistributedMapSource<>(SourceType.S3, null, null, null, s3, null);
    }

    private static String prefixOrEmpty(String path) {
        return path != null ? path : "";
    }

    /** Resolved S3 source configuration. */
    public record S3SourceConfig(
            String bucket,
            String key,
            String prefix,
            Transform transform,
            Format format,
            CsvFormat csvFormat,
            String expectedBucketOwner) {

        S3SourceConfig withExpectedBucketOwner(String owner) {
            return new S3SourceConfig(bucket, key, prefix, transform, format, csvFormat, owner);
        }
    }

    /** Resolved reader-function source configuration. */
    public record ReaderSourceConfig(String functionName, Object initialState, SerDes stateSerDes) {
        public ReaderSourceConfig {
            DistributedMapValidation.validateFunctionName(functionName);
        }
    }
}
