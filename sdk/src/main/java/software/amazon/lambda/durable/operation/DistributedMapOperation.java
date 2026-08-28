// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.operation;

// Written against DistributedMap* shapes not yet in the generated Lambda client, so this does not compile until they ship.

import java.util.function.Function;
import software.amazon.awssdk.services.lambda.model.DistributedMapDetails;
import software.amazon.awssdk.services.lambda.model.DistributedMapOptions;
import software.amazon.awssdk.services.lambda.model.Operation;
import software.amazon.awssdk.services.lambda.model.OperationAction;
import software.amazon.awssdk.services.lambda.model.OperationUpdate;
import software.amazon.lambda.durable.TypeToken;
import software.amazon.lambda.durable.context.DurableContextImpl;
import software.amazon.lambda.durable.exception.DistributedMapException;
import software.amazon.lambda.durable.model.OperationIdentifier;
import software.amazon.lambda.durable.serde.SerDes;

/**
 * Durable operation that starts a distributed map run and waits for its terminal outcome.
 *
 * @param <R> the resolved result type (a summary, or a result with collected items)
 */
public class DistributedMapOperation<R> extends SerializableDurableOperation<R> {
    private final DistributedMapOptions options;
    private final Function<DistributedMapDetails, R> resultBuilder;

    public DistributedMapOperation(
            OperationIdentifier operationIdentifier,
            DistributedMapOptions options,
            TypeToken<R> resultType,
            SerDes serDes,
            Function<DistributedMapDetails, R> resultBuilder,
            DurableContextImpl durableContext) {
        super(operationIdentifier, resultType, serDes, durableContext);
        this.options = options;
        this.resultBuilder = resultBuilder;
    }

    /** Starts the operation. */
    @Override
    protected void start() {
        sendOperationUpdate(OperationUpdate.builder().action(OperationAction.START).distributedMapOptions(options));
        pollForOperationUpdates();
    }

    /** Replays the operation. */
    @Override
    protected void replay(Operation existing) {
        switch (existing.status()) {
            case STARTED -> pollForOperationUpdates();
            case SUCCEEDED, FAILED, TIMED_OUT, STOPPED -> markAlreadyCompleted();
            default ->
                throw terminateExecutionWithIllegalDurableOperationException(
                        "Unexpected distributed map status: " + existing.statusAsString());
        }
    }

    /** Blocks until the operation completes and returns the resolved outcome. */
    @Override
    public R get() {
        var op = waitForOperationCompletion();
        return switch (op.status()) {
            case SUCCEEDED -> {
                var details = op.distributedMapDetails();
                if (details == null) {
                    throw terminateExecutionWithIllegalDurableOperationException(
                            "DISTRIBUTED_MAP operation succeeded but carried no DistributedMapDetails");
                }
                yield resultBuilder.apply(details);
            }
            // Reaching here means the operation itself terminal-failed, so raise instead of hanging.
            case FAILED, TIMED_OUT, STOPPED -> throw new DistributedMapException(op);
            default ->
                throw terminateExecutionWithIllegalDurableOperationException(
                        "Unexpected distributed map operation status: " + op.statusAsString());
        };
    }
}
