// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.dmap;

import java.util.Collections;
import java.util.List;

/** A page of items and the next state returned by a distributed map reader function. */
public record ReaderPage<I, S>(List<I> items, S nextState) {

    /** Applies a defensive copy and defaults. */
    public ReaderPage {
        items = items != null ? List.copyOf(items) : Collections.emptyList();
    }
}
