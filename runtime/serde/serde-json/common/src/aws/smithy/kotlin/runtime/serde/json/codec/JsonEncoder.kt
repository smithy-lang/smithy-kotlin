/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.json.codec

import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.io.SdkBufferedSink
import aws.smithy.kotlin.runtime.serde.StructuredSinkWriter
import aws.smithy.kotlin.runtime.serde.codecs.Encoder
import aws.smithy.kotlin.runtime.serde.codecs.KeyValueEncoder
import aws.smithy.kotlin.runtime.serde.json.escape

internal enum class EncoderState(val addDelimiters: Boolean) {
    VALUE(false),
    ARRAY(true),
    OBJECT(true),
}

public class JsonEncoder internal constructor(
    private val writer: StructuredSinkWriter,
    private val state: EncoderState,
) : Encoder, KeyValueEncoder {
    public constructor(sink: SdkBufferedSink) : this(StructuredSinkWriter(sink), EncoderState.VALUE)

    private var encodeCount = 0

    private fun increment() {
        check(state == EncoderState.VALUE && encodeCount > 0) { "Value encoders must only encode a single value!" }

        if (encodeCount == 0 && state.addDelimiters) {
            writer.write(",")
            writer.newline()
        }

        encodeCount++
    }

    private fun withChild(state: EncoderState, block: (JsonEncoder) -> Unit) {
        block(JsonEncoder(writer, state))
    }

    override fun encodeBigDecimal(value: BigDecimal) {
        increment()
        writer.write(value.toString())
    }

    override fun encodeBigInteger(value: BigInteger) {
        increment()
        writer.write(value.toString())
    }

    override fun encodeBoolean(value: Boolean) {
        increment()
        writer.write(value.toString())
    }

    override fun encodeByte(value: Byte) {
        increment()
        writer.write(value.toString())
    }

    override fun encodeByteStream(value: ByteStream) {
        error("JSON format does not support byte streams")
    }

    override fun encodeDouble(value: Double) {
        increment()
        writer.write(value.toString())
    }

    override fun encodeEntry(keyBlock: (Encoder) -> Unit, valueBlock: (Encoder) -> Unit) {
        increment()
        withChild(EncoderState.VALUE) { keyEncoder -> keyBlock(keyEncoder) }
        writer.write(": ")
        withChild(EncoderState.VALUE) { valueEncoder -> valueBlock(valueEncoder) }
    }

    override fun encodeFloat(value: Float) {
        increment()
        writer.write(value.toString())
    }

    override fun encodeInt(value: Int) {
        increment()
        writer.write(value.toString())
    }

    override fun encodeList(elementBlock: (Encoder) -> Unit) {
        increment()
        writer.withBlock("[", "]") {
            withChild(EncoderState.ARRAY) { elementEncoder -> elementBlock(elementEncoder) }
        }
    }

    override fun encodeLong(value: Long) {
        increment()
        writer.write(value.toString())
    }

    override fun encodeMap(entryBlock: (KeyValueEncoder) -> Unit) {
        increment()
        writer.withBlock("{", "}") {
            withChild(EncoderState.OBJECT) { entryEncoder -> entryBlock(entryEncoder) }
        }
    }

    override fun encodeNull() {
        increment()
        writer.write("null")
    }

    override fun encodeShort(value: Short) {
        increment()
        writer.write(value.toString())
    }

    override fun encodeString(value: String) {
        increment()
        writer.write("\"${value.escape()}\"")
    }

    override fun encodeStructure(memberBlock: (KeyValueEncoder) -> Unit) {
        increment()
        writer.withBlock("{", "}") {
            withChild(EncoderState.OBJECT) { memberEncoder -> memberBlock(memberEncoder) }
        }
    }
}
