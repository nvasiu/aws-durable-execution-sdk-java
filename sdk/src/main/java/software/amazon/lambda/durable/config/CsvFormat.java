// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.config;

import java.util.List;
import software.amazon.lambda.durable.util.DistributedMapValidation;

/** CSV parsing options for a distributed map S3 source. */
public record CsvFormat(HeaderLocation headerLocation, List<String> columns, CsvDelimiter delimiter) {

    /** Where the column header row comes from. */
    public enum HeaderLocation {
        FIRST_ROW,
        GIVEN
    }

    public CsvFormat {
        columns = columns != null ? List.copyOf(columns) : null;
        delimiter = delimiter != null ? delimiter : CsvDelimiter.COMMA;
    }

    /**
     * File has its own header row. Column names are used only for compile-time typing and are not sent on the wire.
     */
    public static CsvFormat expectedColumns(List<String> columns) {
        DistributedMapValidation.validateColumns("expectedColumns", columns);
        return new CsvFormat(HeaderLocation.FIRST_ROW, columns, CsvDelimiter.COMMA);
    }

    /** File has no header row. The given names are the columns, sent on the wire, and the first row is data. */
    public static CsvFormat headers(List<String> headers) {
        DistributedMapValidation.validateColumns("headers", headers);
        return new CsvFormat(HeaderLocation.GIVEN, headers, CsvDelimiter.COMMA);
    }

    /** File has its own header row and no column names are declared. */
    public static CsvFormat firstRow() {
        return new CsvFormat(HeaderLocation.FIRST_ROW, null, CsvDelimiter.COMMA);
    }

    /** Returns a copy with the given delimiter. */
    public CsvFormat withDelimiter(CsvDelimiter delimiter) {
        return new CsvFormat(headerLocation, columns, delimiter);
    }
}
