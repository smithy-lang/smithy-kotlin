/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*

class XmlSerializer(private val xmlWriter: XmlStreamWriter = xmlStreamWriter()) : Serializer, StructSerializer {

    private var nodeStack = mutableListOf<String>()

    override fun toByteArray(): ByteArray {
        return xmlWriter.bytes
    }

    override fun beginStruct(descriptor: SdkFieldDescriptor): StructSerializer {
        xmlWriter.startTag(descriptor.serialName)

        nodeStack.add(descriptor.serialName)

        return this
    }

    override fun beginList(descriptor: SdkFieldDescriptor): ListSerializer {
        xmlWriter.startTag(descriptor.serialName)
        return XmlListSerializer(descriptor, xmlWriter, this)
    }

    override fun beginMap(descriptor: SdkFieldDescriptor): MapSerializer {
        val mapTrait = descriptor.expectTrait<XmlMap>()

        if (!mapTrait.flattened) {
            xmlWriter.startTag(descriptor.serialName)
        }
        return XmlMapSerializer(descriptor, xmlWriter, this)
    }

    override fun endStruct() {
        check(nodeStack.isNotEmpty()) { "Expected nodeStack to have a value, but was empty." }
        xmlWriter.endTag(nodeStack.removeAt(nodeStack.size - 1))
    }

    override fun field(descriptor: SdkFieldDescriptor, value: SdkSerializable) = value.serialize(this)

    override fun field(descriptor: SdkFieldDescriptor, value: Int) {
        xmlWriter.startTag(descriptor.serialName)
        serializeInt(value)
        xmlWriter.endTag(descriptor.serialName)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Long) {
        xmlWriter.startTag(descriptor.serialName)
        serializeLong(value)
        xmlWriter.endTag(descriptor.serialName)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Float) {
        xmlWriter.startTag(descriptor.serialName)
        serializeFloat(value)
        xmlWriter.endTag(descriptor.serialName)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: String) {
        xmlWriter.startTag(descriptor.serialName)
        serializeString(value)
        xmlWriter.endTag(descriptor.serialName)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Double) {
        xmlWriter.startTag(descriptor.serialName)
        serializeDouble(value)
        xmlWriter.endTag(descriptor.serialName)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Boolean) {
        xmlWriter.startTag(descriptor.serialName)
        serializeBoolean(value)
        xmlWriter.endTag(descriptor.serialName)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Byte) {
        xmlWriter.startTag(descriptor.serialName)
        serializeByte(value)
        xmlWriter.endTag(descriptor.serialName)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Short) {
        xmlWriter.startTag(descriptor.serialName)
        serializeShort(value)
        xmlWriter.endTag(descriptor.serialName)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Char) {
        xmlWriter.startTag(descriptor.serialName)
        serializeChar(value)
        xmlWriter.endTag(descriptor.serialName)
    }

    override fun structField(descriptor: SdkFieldDescriptor, block: StructSerializer.() -> Unit) {
        serializeStruct(descriptor, block)
    }

    override fun listField(descriptor: SdkFieldDescriptor, block: ListSerializer.() -> Unit) {
        xmlWriter.startTag(descriptor.serialName)
        val s = XmlListSerializer(descriptor, xmlWriter, this)
        block(s)
        xmlWriter.endTag(descriptor.serialName)
    }

    override fun mapField(descriptor: SdkFieldDescriptor, block: MapSerializer.() -> Unit) {
        serializeMap(descriptor, block)
    }

    override fun serializeNull(descriptor: SdkFieldDescriptor) {
        // This might  be represented w/ attrib 'xsi:nil="true"'
        // NOP
    }

    override fun serializeBoolean(value: Boolean) = xmlWriter.text(value)

    override fun serializeByte(value: Byte) = xmlWriter.text(value)

    override fun serializeShort(value: Short) = xmlWriter.text(value)

    override fun serializeChar(value: Char) {
        xmlWriter.text(value.toString())
    }

    override fun serializeInt(value: Int) = xmlWriter.text(value)

    override fun serializeLong(value: Long) = xmlWriter.text(value)

    override fun serializeFloat(value: Float) = xmlWriter.text(value)

    override fun serializeDouble(value: Double) = xmlWriter.text(value)

    override fun serializeString(value: String) {
        xmlWriter.text(value)
    }

    override fun serializeSdkSerializable(value: SdkSerializable) = value.serialize(this)
}

private class XmlMapSerializer(
    private val descriptor: SdkFieldDescriptor,
    private val xmlWriter: XmlStreamWriter,
    private val xmlSerializer: Serializer
) : MapSerializer {

    override fun endMap() {
        val mapTrait = descriptor.expectTrait<XmlMap>()
        if (!mapTrait.flattened) {
            xmlWriter.endTag(descriptor.serialName)
        }
    }

    fun generalEntry(key: String, valueFn: () -> Unit) {
        val mapTrait = descriptor.expectTrait<XmlMap>()

        xmlWriter.startTag(mapTrait.entry)
        xmlWriter.startTag(mapTrait.keyName)
        xmlWriter.text(key)
        xmlWriter.endTag(mapTrait.keyName)
        xmlWriter.startTag(mapTrait.valueName)
        valueFn()
        xmlWriter.endTag(mapTrait.valueName)
        xmlWriter.endTag(mapTrait.entry)
    }

    override fun entry(key: String, value: Int) = generalEntry(key) { xmlWriter.text(value.toString()) }

    override fun entry(key: String, value: Long) = generalEntry(key) { xmlWriter.text(value.toString()) }

    override fun entry(key: String, value: Float) = generalEntry(key) { xmlWriter.text(value.toString()) }

    override fun entry(key: String, value: String) = generalEntry(key) { xmlWriter.text(value) }

    override fun entry(key: String, value: SdkSerializable) = generalEntry(key) { value.serialize(xmlSerializer) }

    override fun entry(key: String, value: Double) = generalEntry(key) { xmlWriter.text(value.toString()) }

    override fun entry(key: String, value: Boolean) = generalEntry(key) { xmlWriter.text(value.toString()) }

    override fun entry(key: String, value: Byte) = generalEntry(key) { xmlWriter.text(value.toString()) }

    override fun entry(key: String, value: Short) = generalEntry(key) { xmlWriter.text(value.toString()) }

    override fun entry(key: String, value: Char) = generalEntry(key) { xmlWriter.text(value.toString()) }

    override fun serializeBoolean(value: Boolean) = serializePrimitive(value)

    override fun serializeByte(value: Byte) = serializePrimitive(value)

    override fun serializeShort(value: Short) = serializePrimitive(value)

    override fun serializeChar(value: Char) = serializePrimitive(value)

    override fun serializeInt(value: Int) = serializePrimitive(value)

    override fun serializeLong(value: Long) = serializePrimitive(value)

    override fun serializeFloat(value: Float) = serializePrimitive(value)

    override fun serializeDouble(value: Double) = serializePrimitive(value)

    override fun serializeString(value: String) = serializePrimitive(value)

    override fun serializeSdkSerializable(value: SdkSerializable) = value.serialize(xmlSerializer)

    override fun serializeNull(descriptor: SdkFieldDescriptor) {
        TODO("Not yet implemented")
    }

    private fun serializePrimitive(value: Any?) {
        val nodeName = descriptor.expectTrait<XmlMap>().valueName
        xmlWriter.startTag(nodeName)
        xmlWriter.text(value?.toString() ?: "") // NOTE: this may not be the correct serialization format for `null`
        xmlWriter.endTag(nodeName)
    }
}

private class XmlListSerializer(
    private val descriptor: SdkFieldDescriptor,
    private val xmlWriter: XmlStreamWriter,
    private val xmlSerializer: Serializer
) : ListSerializer {

    override fun endList() {
        xmlWriter.endTag(descriptor.serialName)
    }

    override fun serializeBoolean(value: Boolean) = serializePrimitive(value)

    override fun serializeByte(value: Byte) = serializePrimitive(value)

    override fun serializeShort(value: Short) = serializePrimitive(value)

    override fun serializeChar(value: Char) = serializePrimitive(value)

    override fun serializeInt(value: Int) = serializePrimitive(value)

    override fun serializeLong(value: Long) = serializePrimitive(value)

    override fun serializeFloat(value: Float) = serializePrimitive(value)

    override fun serializeDouble(value: Double) = serializePrimitive(value)

    override fun serializeString(value: String) = serializePrimitive(value)

    override fun serializeSdkSerializable(value: SdkSerializable) = value.serialize(xmlSerializer)

    override fun serializeNull(descriptor: SdkFieldDescriptor) {
        TODO("Not yet implemented")
    }

    private fun serializePrimitive(value: Any?) {
        val nodeName = descriptor.expectTrait<XmlList>().elementName
        xmlWriter.startTag(nodeName)
        xmlWriter.text(value?.toString() ?: "") // NOTE: this may not be the correct serialization format for `null`
        xmlWriter.endTag(nodeName)
    }
}
