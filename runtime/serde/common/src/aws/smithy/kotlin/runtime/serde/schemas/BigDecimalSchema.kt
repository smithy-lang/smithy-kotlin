/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schemas

import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.serde.ShapeId
import aws.smithy.kotlin.runtime.serde.ShapeType
import aws.smithy.kotlin.runtime.serde.Trait
import aws.smithy.kotlin.runtime.serde.codecs.Decoder
import aws.smithy.kotlin.runtime.serde.codecs.Encoder

public interface BigDecimalSchema : SerializableSchema<BigDecimal>

private data class BigDecimalSchemaImpl(
    override val shapeId: ShapeId,
    override val traits: List<Trait>,
) : BigDecimalSchema {
    override val shapeType: ShapeType = ShapeType.BIG_DECIMAL
    override fun deserialize(decoder: Decoder): BigDecimal = decoder.decodeBigDecimal()
    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeBigDecimal(value)
}

public fun BigDecimalSchema(
    shapeId: ShapeId,
    traits: List<Trait>,
): BigDecimalSchema = BigDecimalSchemaImpl(shapeId, traits)
