/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*
import software.aws.clientrt.serde.xml.dom.Stack
import software.aws.clientrt.serde.xml.dom.peekOrNull
import software.aws.clientrt.serde.xml.dom.pop
import software.aws.clientrt.serde.xml.dom.push

/**
 * Provides serialization for the XML message format.
 * @param xmlWriter where content is serialize to
 */
// TODO - mark class internal and remove integration tests once serde is stable
class XmlSerializer(private val xmlWriter: XmlStreamWriter = xmlStreamWriter()) : Serializer, StructSerializer {

    private var nodeStack = mutableListOf<XmlSerialName>()
    private var memberDescriptorStack: Stack<SdkFieldDescriptor> = mutableListOf()

    override fun toByteArray(): ByteArray {
        return xmlWriter.bytes
    }

    override fun beginStruct(descriptor: SdkFieldDescriptor): StructSerializer {
        // if we are serializing a nested structure field, use the member descriptor instead of the object descriptor
        val structDescriptor = memberDescriptorStack.peekOrNull() ?: descriptor

        structDescriptor.findTrait<XmlNamespace>()?.let { xmlNamespace ->
            xmlWriter.namespacePrefix(xmlNamespace.uri, xmlNamespace.prefix)
        }

        xmlWriter.startTag(structDescriptor.serialName)

        nodeStack.add(structDescriptor.serialName)

        return this
    }

    override fun beginList(descriptor: SdkFieldDescriptor): ListSerializer {
        if (!descriptor.hasTrait<Flattened>()) xmlWriter.startTag(descriptor.serialName)
        return XmlListSerializer(descriptor, xmlWriter, this)
    }

    override fun beginMap(descriptor: SdkFieldDescriptor): MapSerializer {
        if (!descriptor.hasTrait<Flattened>()) {
            xmlWriter.startTag(descriptor.serialName)
        }
        return XmlMapSerializer(descriptor, xmlWriter, this)
    }

    override fun endStruct() {
        check(nodeStack.isNotEmpty()) { "Expected nodeStack to have a value, but was empty." }
        xmlWriter.endTag(nodeStack.removeAt(nodeStack.size - 1))
    }

    override fun field(descriptor: SdkFieldDescriptor, value: SdkSerializable) {
        memberDescriptorStack.push(descriptor)
        value.serialize(this)
        memberDescriptorStack.pop()
    }

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

    override fun rawField(descriptor: SdkFieldDescriptor, value: String) = field(descriptor, value)

    override fun nullField(descriptor: SdkFieldDescriptor) {
        xmlWriter.startTag(descriptor.serialName)
        serializeNull()
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

    override fun serializeNull() {
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

    override fun serializeRaw(value: String) {
        xmlWriter.text(value)
    }

    override fun serializeSdkSerializable(value: SdkSerializable) = value.serialize(this)
}

private fun XmlStreamWriter.startTag(name: XmlSerialName) = startTag(name.name)
private fun XmlStreamWriter.endTag(serialName: XmlSerialName) = endTag(serialName.name)

private class XmlMapSerializer(
    private val descriptor: SdkFieldDescriptor,
    private val xmlWriter: XmlStreamWriter,
    private val xmlSerializer: Serializer
) : MapSerializer {

    override fun endMap() {
        if (!descriptor.hasTrait<Flattened>()) {
            xmlWriter.endTag(descriptor.serialName)
        }
    }

    fun generalEntry(key: String, valueFn: () -> Unit) {
        val mapTrait = descriptor.findTrait() ?: XmlMapName.Default

        val tagName = if (descriptor.hasTrait<Flattened>()) {
            descriptor.serialName.name
        } else {
            checkNotNull(mapTrait.entry)
        }

        xmlWriter.writeTag(tagName) {
            writeTag(mapTrait.key) { text(key) }
            writeTag(mapTrait.value) { valueFn() }
        }
    }

    override fun entry(key: String, value: Int?) = generalEntry(key) { xmlWriter.text(value.toString()) }

    override fun entry(key: String, value: Long?) = generalEntry(key) { xmlWriter.text(value.toString()) }

    override fun entry(key: String, value: Float?) = generalEntry(key) { xmlWriter.text(value.toString()) }

    override fun entry(key: String, value: String?) = generalEntry(key) { xmlWriter.text(value ?: "") }

    override fun entry(key: String, value: SdkSerializable?) = generalEntry(key) { value?.serialize(xmlSerializer) ?: xmlWriter.text("") }

    override fun entry(key: String, value: Double?) = generalEntry(key) { xmlWriter.text(value.toString()) }

    override fun entry(key: String, value: Boolean?) = generalEntry(key) { xmlWriter.text(value.toString()) }

    override fun entry(key: String, value: Byte?) = generalEntry(key) { xmlWriter.text(value.toString()) }

    override fun entry(key: String, value: Short?) = generalEntry(key) { xmlWriter.text(value.toString()) }

    override fun entry(key: String, value: Char?) = generalEntry(key) { xmlWriter.text(value.toString()) }

    override fun listEntry(key: String, listDescriptor: SdkFieldDescriptor, block: ListSerializer.() -> Unit) {
        generalEntry(key) {
            val ls = xmlSerializer.beginList(listDescriptor)
            block.invoke(ls)
            ls.endList()
        }
    }

    override fun mapEntry(key: String, mapDescriptor: SdkFieldDescriptor, block: MapSerializer.() -> Unit) {
        generalEntry(key) {
            val ls = xmlSerializer.beginMap(mapDescriptor)
            block.invoke(ls)
            ls.endMap()
        }
    }

    override fun rawEntry(key: String, value: String) =
        entry(key, value)

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

    override fun serializeRaw(value: String) = serializeString(value)

    override fun serializeNull() {
        val nodeName = descriptor.findTrait<XmlMapName>()?.value ?: XmlMapName.Default.value
        xmlWriter.startTag(nodeName)
        xmlWriter.endTag(nodeName)
    }

    private fun serializePrimitive(value: Any) {
        val nodeName = descriptor.findTrait<XmlMapName>()?.value ?: XmlMapName.Default.value
        xmlWriter.startTag(nodeName)
        xmlWriter.text(value.toString())
        xmlWriter.endTag(nodeName)
    }
}

private class XmlListSerializer(
    private val descriptor: SdkFieldDescriptor,
    private val xmlWriter: XmlStreamWriter,
    private val xmlSerializer: Serializer
) : ListSerializer {

    override fun endList() {
        if (!descriptor.hasTrait<Flattened>()) xmlWriter.endTag(descriptor.serialName)
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

    override fun serializeNull() {
        val nodeName = descriptor.findTrait<XmlCollectionName>()?.element ?: XmlCollectionName.Default.element
        xmlWriter.startTag(nodeName)
        xmlWriter.endTag(nodeName)
    }

    override fun serializeRaw(value: String) = serializeString(value)

    private fun serializePrimitive(value: Any) {
        val nodeName = descriptor.findTrait<XmlCollectionName>()?.element ?: XmlCollectionName.Default.element
        xmlWriter.startTag(nodeName)
        xmlWriter.text(value.toString())
        xmlWriter.endTag(nodeName)
    }
}

/**
 * Write start tag, call [block] to fill contents, writes end tag
 */
private fun XmlStreamWriter.writeTag(name: String, block: XmlStreamWriter.() -> Unit) {
    startTag(name)
    apply(block)
    endTag(name)
}
