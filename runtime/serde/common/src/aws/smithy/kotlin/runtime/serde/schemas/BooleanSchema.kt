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

public interface BooleanSchema : SerializableSchema<Boolean>

private data class BooleanSchemaImpl(
    override val shapeId: ShapeId,
    override val traits: List<Trait>,
) : BooleanSchema {
    override val shapeType: ShapeType = ShapeType.BOOLEAN
    override fun deserialize(decoder: Decoder): Boolean = decoder.decodeBoolean()
    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
}

public fun BooleanSchema(shapeId: ShapeId, traits: List<Trait>): BooleanSchema = BooleanSchemaImpl(shapeId, traits)
