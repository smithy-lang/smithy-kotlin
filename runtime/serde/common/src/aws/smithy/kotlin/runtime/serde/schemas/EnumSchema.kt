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

public interface EnumSchema<E> : SerializableSchema<E>

private data class EnumSchemaImpl<E, V>(
    private val delegateSchema: SerializableSchema<V>,
    private val factory: (V) -> E,
    private val valueGetter: (E) -> V,
) : EnumSchema<E> {
    override val shapeId: ShapeId = delegateSchema.shapeId
    override val shapeType: ShapeType = delegateSchema.shapeType
    override val traits: List<Trait> = delegateSchema.traits

    override fun deserialize(decoder: Decoder): E {
        val identifier = delegateSchema.deserialize(decoder)
        return factory(identifier)
    }

    override fun serialize(encoder: Encoder, value: E) {
        val identifier = valueGetter(value)
        delegateSchema.serialize(encoder, identifier)
    }
}

@JvmName("IntEnumSchema")
public fun <E> EnumSchema(
    delegateSchema: SerializableSchema<Int>,
    factory: (Int) -> E,
    valueGetter: (E) -> Int,
): EnumSchema<E> = EnumSchemaImpl(delegateSchema, factory, valueGetter)

public fun <E> IntegerSchema.asEnum(
    factory: (Int) -> E,
    valueGetter: (E) -> Int,
): EnumSchema<E> = EnumSchema(this, factory, valueGetter)

@JvmName("StringEnumSchema")
public fun <E> EnumSchema(
    delegateSchema: SerializableSchema<String>,
    factory: (String) -> E,
    valueGetter: (E) -> String,
): EnumSchema<E> = EnumSchemaImpl(delegateSchema, factory, valueGetter)

public fun <E> StringSchema.asEnum(
    factory: (String) -> E,
    valueGetter: (E) -> String,
): EnumSchema<E> = EnumSchema(this, factory, valueGetter)
