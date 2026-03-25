/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schemas

import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.asByteStream
import aws.smithy.kotlin.runtime.serde.ShapeId
import aws.smithy.kotlin.runtime.serde.ShapeType
import aws.smithy.kotlin.runtime.serde.Trait
import aws.smithy.kotlin.runtime.serde.codecs.Decoder
import aws.smithy.kotlin.runtime.serde.codecs.Encoder

public object BlobSchema {
    public interface Inline: SerializableSchema<ByteArray>

    private data class BlobSchemaInlineImpl(
        override val shapeId: ShapeId,
        override val traits: List<Trait>,
    ) : Inline {
        override val shapeType: ShapeType = ShapeType.BLOB

        override fun deserialize(decoder: Decoder): ByteArray {
            val stream = decoder.decodeByteStream()
            check(stream is ByteStream.Buffer) { "Cannot deserialize a streaming byte payload into an inline blob" }
            return stream.bytes()
        }

        override fun serialize(encoder: Encoder, value: ByteArray) = encoder.encodeByteStream(value.asByteStream())
    }

    public fun Inline(shapeId: ShapeId, traits: List<Trait>): Inline = BlobSchemaInlineImpl(shapeId, traits)

    public interface Streaming: SerializableSchema<ByteStream>

    private data class BlobSchemaStreamingImpl(
        override val shapeId: ShapeId,
        override val traits: List<Trait>,
    ) : Streaming {
        override val shapeType: ShapeType = ShapeType.BLOB
        override fun deserialize(decoder: Decoder): ByteStream = decoder.decodeByteStream()
        override fun serialize(encoder: Encoder, value: ByteStream) = encoder.encodeByteStream(value)
    }

    public fun Streaming(shapeId: ShapeId, traits: List<Trait>): Streaming = BlobSchemaStreamingImpl(shapeId, traits)
}
