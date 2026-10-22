// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.dmap;

import java.util.List;
import software.amazon.lambda.durable.util.ExceptionHelper;

/**
 * A failed item in a processor response. The error is a nested object on the wire, so it is a nested type here.
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
     * @param errorData machine-readable error detail, or null when there is none
     * @param stackTrace the frames where the error was thrown, or null when they were not captured
     */
    public record ErrorDetail(String errorType, String errorMessage, String errorData, List<String> stackTrace) {

        /** Builds an error detail from a throwable. */
        public static ErrorDetail of(Throwable e) {
            // errorData stays null. A throwable carries no machine-readable error data, so there is nothing
            // to put here.
            return new ErrorDetail(
                    e.getClass().getName(),
                    e.getMessage(),
                    null,
                    ExceptionHelper.serializeStackTrace(e.getStackTrace()));
        }
    }
}
