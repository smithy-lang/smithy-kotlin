/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema.serde

import aws.smithy.kotlin.runtime.time.TimestampFormat

/**
 * The wire behavior of one byte format that is not derivable from the model, shared by every codec.
 *
 * Settings — not separate codec types — are what distinguish protocols that share a format: restJson1 and
 * awsJson1_1 both use the JSON codec and differ only in whether `@jsonName` applies.
 */
public interface CodecSettings {
    /** The timestamp format to use for a shape that does not request one via `@timestampFormat`. */
    public val defaultTimestampFormat: TimestampFormat
}

/**
 * A factory for the [ShapeSerializer]/[ShapeDeserializer] pair of one **byte format** (JSON, XML, CBOR), and
 * the public entry point for serializing a shape outside an operation call.
 *
 * A codec is bound to a format rather than a protocol, is immutable and stateless, and produces only the
 * message payload — HTTP messages and other protocol framing are the `ClientProtocol`'s concern. Codecs are
 * long-lived: construct one per configuration and reuse it, creating a short-lived serializer per message.
 *
 * @param F the serialized form this codec reads and writes, e.g. `ByteArray`.
 */
public interface Codec<F> {
    /** The format behavior this codec was configured with. */
    public val settings: CodecSettings

    /** A fresh serializer accumulating into [F]. */
    public fun createSerializer(): ShapeSerializer<F>

    /**
     * A fresh deserializer reading [source], or `null` if this format is write-only (awsQuery requests are
     * serialized as form-url but responses come back as XML).
     */
    public fun createDeserializer(source: F): ShapeDeserializer?
}

/**
 * Serialize [shape] to this codec's format.
 */
public fun <F> Codec<F>.serialize(shape: SerializableStruct): F {
    val serializer = createSerializer()
    shape.serialize(serializer)
    return serializer.flush()
}

/**
 * Deserialize [source] into [builder] and build the result.
 *
 * @throws IllegalArgumentException if this codec does not support deserialization.
 */
public fun <F, T> Codec<F>.deserialize(source: F, builder: ShapeBuilder<T>): T {
    val deserializer = requireNotNull(createDeserializer(source)) { "$this does not support deserialization" }
    builder.deserialize(deserializer)
    return builder.build()
}
