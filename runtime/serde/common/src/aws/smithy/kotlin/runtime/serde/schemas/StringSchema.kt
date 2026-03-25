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

public interface StringSchema : SerializableSchema<String>

private data class StringSchemaImpl(
    override val shapeId: ShapeId,
    override val traits: List<Trait>,
) : StringSchema {
    override val shapeType: ShapeType = ShapeType.STRING
    override fun deserialize(decoder: Decoder): String = decoder.decodeString()
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

public fun StringSchema(shapeId: ShapeId, traits: List<Trait>): StringSchema = StringSchemaImpl(shapeId, traits)
