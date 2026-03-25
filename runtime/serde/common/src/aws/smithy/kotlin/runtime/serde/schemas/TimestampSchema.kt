/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schemas

import aws.smithy.kotlin.runtime.serde.ShapeId
import aws.smithy.kotlin.runtime.serde.ShapeType
import aws.smithy.kotlin.runtime.serde.Trait
import aws.smithy.kotlin.runtime.serde.codecs.Decoder
import aws.smithy.kotlin.runtime.serde.codecs.Encoder
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.fromEpochMilliseconds

public interface TimestampSchema : SerializableSchema<Instant>

private data class TimestampSchemaImpl(
    override val shapeId: ShapeId,
    override val traits: List<Trait>,
) : TimestampSchema {
    override val shapeType: ShapeType = ShapeType.TIMESTAMP
    override fun deserialize(decoder: Decoder): Instant = Instant.fromEpochMilliseconds(decoder.decodeLong())
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeLong(value.epochSeconds)
}

public fun TimestampSchema(
    shapeId: ShapeId,
    traits: List<Trait>,
): TimestampSchema = TimestampSchemaImpl(shapeId, traits)
