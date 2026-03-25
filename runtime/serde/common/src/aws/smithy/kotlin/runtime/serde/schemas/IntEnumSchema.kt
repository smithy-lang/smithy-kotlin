/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schemas

/*
import aws.smithy.kotlin.runtime.serde.ShapeId
import aws.smithy.kotlin.runtime.serde.ShapeType
import aws.smithy.kotlin.runtime.serde.Trait
import aws.smithy.kotlin.runtime.serde.codecs.Decoder
import aws.smithy.kotlin.runtime.serde.codecs.Encoder

public interface IntEnumSchema<E> : SerializableSchema<E>

private data class IntEnumSchemaImpl<E>(
    private val delegateSchema: SerializableSchema<Int>,
    private val factory: (Int) -> E,
    private val valueGetter: (E) -> Int,
) : EnumSchema<E> {
    override val shapeId: ShapeId = delegateSchema.shapeId
    override val shapeType: ShapeType = ShapeType.ENUM
    override val traits: List<Trait> = delegateSchema.traits

    override fun deserialize(decoder: Decoder): E {
        val intValue = delegateSchema.deserialize(decoder)
        return factory(intValue)
    }

    override fun serialize(encoder: Encoder, value: E) {
        val intValueValue = valueGetter(value)
        delegateSchema.serialize(encoder, stringValue)
    }
}

public fun <E> EnumSchema(
    delegateSchema: SerializableSchema<String>,
    factory: (String) -> E,
    valueGetter: (E) -> String,
): EnumSchema<E> = EnumSchemaImpl(delegateSchema, factory, valueGetter)

public fun <E> StringSchema.asEnum(
    factory: (String) -> E,
    valueGetter: (E) -> String,
): EnumSchema<E> = EnumSchema(this, factory, valueGetter)
*/