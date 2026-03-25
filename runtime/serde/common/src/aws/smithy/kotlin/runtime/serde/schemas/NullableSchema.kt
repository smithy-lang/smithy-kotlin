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

public interface NullableSchema<T : Any> : SerializableSchema<T?>

private data class NullableSchemaImpl<T : Any>(val delegateSchema: SerializableSchema<T>) : NullableSchema<T> {
    override val shapeId: ShapeId = delegateSchema.shapeId
    override val shapeType: ShapeType = delegateSchema.shapeType
    override val traits: List<Trait> = delegateSchema.traits

    override fun deserialize(decoder: Decoder): T? {
        TODO("Not yet implemented")
    }

    override fun serialize(encoder: Encoder, value: T?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            delegateSchema.serialize(encoder, value)
        }
    }
}

public fun <T : Any> NullableSchema(delegateSchema: SerializableSchema<T>): NullableSchema<T> =
    NullableSchemaImpl(delegateSchema)

public fun <T : Any> SerializableSchema<T>.asNullable(): NullableSchema<T> = NullableSchema(this)
