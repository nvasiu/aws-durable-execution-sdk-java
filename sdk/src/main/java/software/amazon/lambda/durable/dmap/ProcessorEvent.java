// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.dmap;

import java.util.List;

/**
 * The batch the fan-out service sends to a processor function.
 *
 * @param records the items in this batch
 */
public record ProcessorEvent(List<ProcessorRecord> records) {

    /**
     * Rejects an event that is not a processor batch. This runs on instances the Lambda runtime deserializes as well as
     * on hand-constructed ones, because a record can only be built through its canonical constructor.
     */
    public ProcessorEvent {
        if (records == null) {
            throw new IllegalStateException("expected a distributed map processor event carrying a 'records' array, "
                    + "the function must be triggered by a distributed map, not invoked directly. An event source "
                    + "such as SQS or S3 sends a capitalised 'Records' array, which is a different contract");
        }
    }
}
