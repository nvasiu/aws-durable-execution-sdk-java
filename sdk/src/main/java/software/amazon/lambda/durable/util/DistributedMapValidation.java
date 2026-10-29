// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.util;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

/** Shared validation and S3 URI parsing for distributed map configuration. */
public final class DistributedMapValidation {
    private static final Pattern ACCOUNT_ID = Pattern.compile("\\d{12}");
    private static final String S3_SCHEME = "s3://";
    private static final int MAX_FUNCTION_NAME_LENGTH = 170;
    private static final int MAX_CSV_COLUMNS = 1000;
    private static final int MAX_CSV_COLUMN_LENGTH = 256;
    private static final int MAX_DESTINATION_KEY_PREFIX_BYTES = 900;

    private DistributedMapValidation() {}

    public static void validateFunctionName(String functionName) {
        if (functionName == null || functionName.isBlank()) {
            throw new IllegalArgumentException("function name cannot be empty");
        }
        if (functionName.length() > MAX_FUNCTION_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "function name must be at most 170 characters, got: " + functionName.length());
        }
    }

    public static void validateBucketOwner(String expectedBucketOwner) {
        if (expectedBucketOwner != null
                && !ACCOUNT_ID.matcher(expectedBucketOwner).matches()) {
            throw new IllegalArgumentException(
                    "expectedBucketOwner must be a 12-digit account id, got: " + expectedBucketOwner);
        }
    }

    /**
     * Rejects a destination key prefix the service would reject. The limit is 900 UTF-8 bytes, including a trailing
     * slash when the URI has one, and it is measured in bytes rather than characters, so a multi-byte prefix under 900
     * characters can still exceed it. The service applies the same limit when the start checkpoint is written, so
     * checking it here fails the call that built the destination instead of the operation.
     */
    public static void validateDestinationPrefix(String prefix) {
        if (prefix == null) {
            return;
        }
        var bytes = prefix.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_DESTINATION_KEY_PREFIX_BYTES) {
            throw new IllegalArgumentException("destination key prefix must be at most "
                    + MAX_DESTINATION_KEY_PREFIX_BYTES + " UTF-8 bytes, got: " + bytes);
        }
    }

    public static void validateColumns(String parameterName, List<String> columns) {
        if (columns == null) {
            return;
        }
        if (columns.isEmpty()) {
            throw new IllegalArgumentException(parameterName + " cannot be empty");
        }
        if (columns.size() > MAX_CSV_COLUMNS) {
            throw new IllegalArgumentException(
                    parameterName + " must hold at most " + MAX_CSV_COLUMNS + " names, got: " + columns.size());
        }
        for (var column : columns) {
            if (column != null && column.length() > MAX_CSV_COLUMN_LENGTH) {
                throw new IllegalArgumentException("each " + parameterName + " name must be at most "
                        + MAX_CSV_COLUMN_LENGTH + " characters, got: " + column.length());
            }
        }
        if (new HashSet<>(columns).size() != columns.size()) {
            throw new IllegalArgumentException(parameterName + " must not contain duplicates");
        }
    }

    public static ParsedS3Uri parseS3Uri(String uri) {
        if (uri == null || !uri.startsWith(S3_SCHEME)) {
            throw new IllegalArgumentException("S3 URI must start with s3://, got: " + uri);
        }
        var remainder = uri.substring(S3_SCHEME.length());
        var slash = remainder.indexOf('/');
        var bucket = slash < 0 ? remainder : remainder.substring(0, slash);
        var path = slash < 0 ? null : remainder.substring(slash + 1);
        if (bucket.isEmpty()) {
            throw new IllegalArgumentException("S3 URI must contain a bucket, got: " + uri);
        }
        return new ParsedS3Uri(bucket, path == null || path.isEmpty() ? null : path);
    }

    public record ParsedS3Uri(String bucket, String path) {}
}
