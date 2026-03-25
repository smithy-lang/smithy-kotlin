package aws.smithy.kotlin.runtime.serde.json.codec

import aws.smithy.kotlin.runtime.io.SdkBufferedSink
import aws.smithy.kotlin.runtime.io.SdkBufferedSource
import aws.smithy.kotlin.runtime.io.buffer
import aws.smithy.kotlin.runtime.serde.SmithyTimestampFormat
import aws.smithy.kotlin.runtime.serde.codecs.Codec
import aws.smithy.kotlin.runtime.serde.codecs.Decoder
import aws.smithy.kotlin.runtime.serde.codecs.Encoder

public class JsonCodec private constructor(builder: Builder) : Codec {
    public companion object {
        public operator fun invoke(builder: Builder.() -> Unit): JsonCodec = JsonCodec(Builder().apply(builder))
    }

    public val defaultTimestampFormat: SmithyTimestampFormat = builder.defaultTimestampFormat
    public val useHttpBindings: Boolean = builder.useHttpBindings
    public val useJsonName: Boolean = builder.useJsonName

    public class Builder {
        public var defaultTimestampFormat: SmithyTimestampFormat = SmithyTimestampFormat.EPOCH_SECONDS
        public var useHttpBindings: Boolean = true
        public var useJsonName: Boolean = true
    }

    override fun createDecoder(source: SdkBufferedSource): Decoder {
        TODO("Not yet implemented")
    }

    override fun createEncoder(sink: SdkBufferedSink): Encoder = JsonEncoder(sink.buffer())
}
