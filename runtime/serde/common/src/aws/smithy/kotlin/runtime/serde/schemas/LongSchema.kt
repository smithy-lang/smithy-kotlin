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

public interface LongSchema : SerializableSchema<Long>

private data class LongSchemaImpl(
    override val shapeId: ShapeId,
    override val traits: List<Trait>,
) : LongSchema {
    override val shapeType: ShapeType = ShapeType.LONG
    override fun deserialize(decoder: Decoder): Long = decoder.decodeLong()
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}

public fun LongSchema(shapeId: ShapeId, traits: List<Trait>): LongSchema = LongSchemaImpl(shapeId, traits)
