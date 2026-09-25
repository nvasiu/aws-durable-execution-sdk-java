// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.dmap;

/**
 * A failed item in a processor response. The error is a nested object on the wire, so it is a nested type here rather
 * than flattened fields.
 *
 * @param itemIdentifier the item's identifier, copied from the input record
 * @param error why the item failed, or null to report a failure with no detail
 */
public record ItemFailure(String itemIdentifier, ErrorDetail error) {

    /**
     * Why an item failed.
     *
     * @param errorType the error's type, conventionally a fully qualified class name
     * @param errorMessage the error's message, or null when it has none
     */
    public record ErrorDetail(String errorType, String errorMessage) {

        /** Builds an error detail from a throwable. */
        public static ErrorDetail of(Throwable e) {
            return new ErrorDetail(e.getClass().getName(), e.getMessage());
        }
    }
}
