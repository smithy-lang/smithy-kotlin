/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.content.Document
import aws.smithy.kotlin.runtime.io.SdkSink
import aws.smithy.kotlin.runtime.serde.*
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.TimestampFormat

public class JsonSerializer(private val sink: SdkSink) : Serializer {
    override fun beginStruct(descriptor: SdkFieldDescriptor): StructSerializer {
        TODO("Not yet implemented")
    }

    override fun beginList(descriptor: SdkFieldDescriptor): ListSerializer {
        TODO("Not yet implemented")
    }

    override fun beginMap(descriptor: SdkFieldDescriptor): MapSerializer {
        TODO("Not yet implemented")
    }

    override fun toByteArray(): ByteArray {
        TODO("Not yet implemented")
    }

    override fun serializeBoolean(value: Boolean) {
        TODO("Not yet implemented")
    }

    override fun serializeByte(value: Byte) {
        TODO("Not yet implemented")
    }

    override fun serializeShort(value: Short) {
        TODO("Not yet implemented")
    }

    override fun serializeChar(value: Char) {
        TODO("Not yet implemented")
    }

    override fun serializeInt(value: Int) {
        TODO("Not yet implemented")
    }

    override fun serializeLong(value: Long) {
        TODO("Not yet implemented")
    }

    override fun serializeFloat(value: Float) {
        TODO("Not yet implemented")
    }

    override fun serializeDouble(value: Double) {
        TODO("Not yet implemented")
    }

    override fun serializeBigInteger(value: BigInteger) {
        TODO("Not yet implemented")
    }

    override fun serializeBigDecimal(value: BigDecimal) {
        TODO("Not yet implemented")
    }

    override fun serializeString(value: String) {
        TODO("Not yet implemented")
    }

    override fun serializeInstant(
        value: Instant,
        format: TimestampFormat,
    ) {
        TODO("Not yet implemented")
    }

    override fun serializeByteArray(value: ByteArray) {
        TODO("Not yet implemented")
    }

    override fun serializeSdkSerializable(value: SdkSerializable) {
        TODO("Not yet implemented")
    }

    override fun serializeNull() {
        TODO("Not yet implemented")
    }

    override fun serializeDocument(value: Document?) {
        TODO("Not yet implemented")
    }

}