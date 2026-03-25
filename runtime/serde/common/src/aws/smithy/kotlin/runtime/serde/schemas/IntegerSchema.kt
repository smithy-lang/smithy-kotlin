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

public interface IntegerSchema : SerializableSchema<Int>

private data class IntegerSchemaImpl(
    override val shapeId: ShapeId,
    override val traits: List<Trait>,
) : IntegerSchema {
    override val shapeType: ShapeType = ShapeType.INTEGER
    override fun deserialize(decoder: Decoder): Int = decoder.decodeInt()
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

public fun IntegerSchema(shapeId: ShapeId, traits: List<Trait>): IntegerSchema = IntegerSchemaImpl(shapeId, traits)
