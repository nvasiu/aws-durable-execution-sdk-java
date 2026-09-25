// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.dmap;

import java.util.List;

/**
 * The page a reader function returns.
 *
 * <p>A null nextState is omitted from the JSON rather than sent as null, because the Lambda runtime's serializer
 * excludes null fields. Omitting the key is how the reader contract signals that the source is exhausted.
 *
 * @param items this page's items
 * @param nextState the state to resume from, or null when the source is exhausted
 * @param <I> the item type
 */
public record ReaderResponse<I>(List<I> items, String nextState) {}
