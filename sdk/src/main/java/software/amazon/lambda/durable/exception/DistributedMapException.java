// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.exception;

import software.amazon.awssdk.services.lambda.model.Operation;

/**
 * Operation failure, thrown when a distributed map operation itself terminal-fails (FAILED, TIMED_OUT, or STOPPED).
 * Extends DurableOperationException. See DistributedMapError for the run outcome, which is an aggregate result rather than an operation failure.
 */
public class DistributedMapException extends DurableOperationException {
    public DistributedMapException(Operation operation) {
        super(
                operation,
                null,
                "Distributed map operation " + operation.id() + " ended with status " + operation.statusAsString());
    }
}
