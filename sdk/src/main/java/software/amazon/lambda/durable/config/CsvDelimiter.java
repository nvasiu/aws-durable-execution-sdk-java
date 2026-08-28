// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.config;

/** Delimiter for CSV distributed map sources. */
public enum CsvDelimiter {
    COMMA("COMMA"),
    PIPE("PIPE"),
    SEMICOLON("SEMICOLON"),
    SPACE("SPACE"),
    TAB("TAB");

    private final String value;

    CsvDelimiter(String value) {
        this.value = value;
    }

    /** Returns the wire-format string value. */
    public String getValue() {
        return value;
    }
}
