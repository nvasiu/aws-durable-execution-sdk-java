// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.dmap;

import java.util.List;

/**
 * The reply an item handler returns.
 *
 * <p>A null list is omitted from the JSON rather than sent as null, because the Lambda runtime's serializer excludes
 * null fields. ITEM_FAILURES mode therefore passes null for batchItemResults so the key is absent, matching the
 * protocol. The service treats an absent, null, or empty list identically, so this is a shape choice rather than a
 * behavioural one.
 *
 * @param batchItemResults per-item outputs, or null in ITEM_FAILURES mode
 * @param batchItemFailures the items that failed
 */
public record ItemHandlerResponse(List<ItemResult> batchItemResults, List<ItemFailure> batchItemFailures) {

    /** Builds a response reporting per-item outputs alongside any failures. */
    public static ItemHandlerResponse itemResults(List<ItemResult> results, List<ItemFailure> failures) {
        return new ItemHandlerResponse(results, failures);
    }

    /** Builds a response reporting only failures, leaving the results key off the wire. */
    public static ItemHandlerResponse itemFailures(List<ItemFailure> failures) {
        return new ItemHandlerResponse(null, failures);
    }
}
