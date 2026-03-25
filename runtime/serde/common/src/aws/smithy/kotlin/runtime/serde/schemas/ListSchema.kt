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

public interface ListSchema<T> : SerializableSchema<List<T>> {
    public val member: SerializableSchema<T>
}

private data class ListSchemaImpl<T>(
    override val shapeId: ShapeId,
    override val member: SerializableSchema<T>,
    override val traits: List<Trait>,
) : ListSchema<T> {
    override val shapeType: ShapeType = ShapeType.LIST

    override fun deserialize(decoder: Decoder): List<T> = decoder.decodeList {
        member.deserialize(decoder)
    }

    override fun serialize(encoder: Encoder, value: List<T>) = encoder.encodeList { entryEncoder ->
        value.forEach { entry ->
            member.serialize(entryEncoder, entry)
        }
    }
}

public fun <T> ListSchema(
    shapeId: ShapeId,
    member: SerializableSchema<T>,
    traits: List<Trait>,
): ListSchema<T> = ListSchemaImpl(shapeId, member, traits)
