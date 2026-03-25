/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schemas

import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.serde.ShapeId
import aws.smithy.kotlin.runtime.serde.ShapeType
import aws.smithy.kotlin.runtime.serde.Trait
import aws.smithy.kotlin.runtime.serde.codecs.Decoder
import aws.smithy.kotlin.runtime.serde.codecs.Encoder

public interface BigIntegerSchema : SerializableSchema<BigInteger>

private data class BigIntegerSchemaImpl(
    override val shapeId: ShapeId,
    override val traits: List<Trait>,
) : BigIntegerSchema {
    override val shapeType: ShapeType = ShapeType.BIG_INTEGER
    override fun deserialize(decoder: Decoder): BigInteger = decoder.decodeBigInteger()
    override fun serialize(encoder: Encoder, value: BigInteger) = encoder.encodeBigInteger(value)
}

public fun BigIntegerSchema(
    shapeId: ShapeId,
    traits: List<Trait>,
): BigIntegerSchema = BigIntegerSchemaImpl(shapeId, traits)
