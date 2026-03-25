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

public interface MapSchema<K, V> : SerializableSchema<Map<K, V>> {
    public val key: SerializableSchema<K>
    public val value: SerializableSchema<V>
}

private data class MapSchemaImpl<K, V>(
    override val shapeId: ShapeId,
    override val key: SerializableSchema<K>,
    override val value: SerializableSchema<V>,
    override val traits: List<Trait>,
) : MapSchema<K, V> {
    override val shapeType: ShapeType = ShapeType.MAP

    override fun deserialize(decoder: Decoder): Map<K, V> = decoder.decodeMap(
        { keyDecoder -> key.deserialize(keyDecoder) },
        { valueDecoder -> value.deserialize(valueDecoder) },
    )

    override fun serialize(encoder: Encoder, value: Map<K, V>) = encoder.encodeMap { keyValueEncoder ->
        value.entries.forEach { (k, v) ->
            keyValueEncoder.encodeEntry(
                { keyEncoder -> key.serialize(keyEncoder, k) },
                { valueEncoder -> this@MapSchemaImpl.value.serialize(valueEncoder, v) },
            )
        }
    }
}

public fun <K, V> MapSchema(
    shapeId: ShapeId,
    key: SerializableSchema<K>,
    value: SerializableSchema<V>,
    traits: List<Trait>,
): MapSchema<K, V> = MapSchemaImpl(shapeId, key, value, traits)
