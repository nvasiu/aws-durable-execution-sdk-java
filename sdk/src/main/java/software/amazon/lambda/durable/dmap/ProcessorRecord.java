// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.lambda.durable.dmap;

/**
 * One record of a processor event.
 *
 * @param itemId the item's identifier, assigned by the service
 * @param body the serialized item, as the item serdes produced it
 */
public record ProcessorRecord(String itemId, String body) {}
