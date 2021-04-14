/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*
import software.aws.clientrt.serde.xml.dom.*

/**
 * Provides serialization for the XML message format.
 * @param xmlWriter where content is serialize to
 */
// TODO - mark class internal and remove integration tests once serde is stable
class XmlSerializer(private val xmlWriter: XmlStreamWriter = xmlStreamWriter()) : Serializer, StructSerializer {

    // FIXME - clean up stack to distinguish between mutable/immutable and move to utils? (e.g. MutableStack<T> = mutableStackOf())
    private var nodeStack: Stack<String> = mutableListOf()
    internal var parentDescriptorStack: Stack<SdkFieldDescriptor> = mutableListOf()

    override fun toByteArray(): ByteArray {
        return xmlWriter.bytes
    }

    override fun beginStruct(descriptor: SdkFieldDescriptor): StructSerializer {
        // if we are serializing a nested structure field
        // either through `field(.., SdkSerializable)` or as part of a list member/map entry
        // use the parent descriptor instead of the object descriptor passed to us.
        // The object descriptor is for root nodes, nested structures have their own field descriptor
        // that describes the referred to struct
        val structDescriptor = parentDescriptorStack.peekOrNull() ?: descriptor

        structDescriptor.findTrait<XmlNamespace>()?.let { xmlNamespace ->
            xmlWriter.namespacePrefix(xmlNamespace.uri, xmlNamespace.prefix)
        }

        // if the parent descriptor is from a map we omit the root level node
        // e.g. Map<String, GreetingStruct> goes to:
        // `<value><hi>foo</hi></value>`
        // instead of
        // `<value><GreetingStruct><hi>foo</hi></GreetingStruct></value>`
        //
        if (structDescriptor.kind != SerialKind.Map) {
            xmlWriter.startTag(structDescriptor.tagName)
        }

        nodeStack.push(structDescriptor.tagName)

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
        val tagName = nodeStack.pop()

        if (parentDescriptorStack.isNotEmpty() && parentDescriptorStack.peek().kind != SerialKind.Map) {
            xmlWriter.endTag(tagName)
        }
    }

    override fun field(descriptor: SdkFieldDescriptor, value: SdkSerializable) {
        parentDescriptorStack.push(descriptor)
        value.serialize(this)
        parentDescriptorStack.pop()
    }

    // serialize something as either an attribute of the current tag or create a new tag with the given value
    private fun <T : Any> tagOrAttribute(descriptor: SdkFieldDescriptor, value: T, serdeFn: (T) -> Unit) {
        when {
            descriptor.hasTrait<XmlAttribute>() -> xmlWriter.attribute(descriptor.serialName.name, value.toString())
            else -> xmlWriter.writeTag(descriptor.tagName) { serdeFn(value) }
        }
    }

    private fun numberField(descriptor: SdkFieldDescriptor, value: Number) =
        tagOrAttribute(descriptor, value, ::serializeNumber)

    override fun field(descriptor: SdkFieldDescriptor, value: Boolean) =
        tagOrAttribute(descriptor, value, ::serializeBoolean)

    override fun field(descriptor: SdkFieldDescriptor, value: Byte) = numberField(descriptor, value)

    override fun field(descriptor: SdkFieldDescriptor, value: Char) =
        tagOrAttribute(descriptor, value, ::serializeChar)

    override fun field(descriptor: SdkFieldDescriptor, value: Short) = numberField(descriptor, value)

    override fun field(descriptor: SdkFieldDescriptor, value: Int) = numberField(descriptor, value)

    override fun field(descriptor: SdkFieldDescriptor, value: Long) = numberField(descriptor, value)

    override fun field(descriptor: SdkFieldDescriptor, value: Float) = numberField(descriptor, value)

    override fun field(descriptor: SdkFieldDescriptor, value: Double) = numberField(descriptor, value)

    override fun field(descriptor: SdkFieldDescriptor, value: String) =
        tagOrAttribute(descriptor, value, ::serializeString)

    override fun rawField(descriptor: SdkFieldDescriptor, value: String) = field(descriptor, value)

    override fun nullField(descriptor: SdkFieldDescriptor) {
        xmlWriter.writeTag(descriptor.serialName.name) {
            serializeNull()
        }
    }

    override fun structField(descriptor: SdkFieldDescriptor, block: StructSerializer.() -> Unit) {
        serializeStruct(descriptor, block)
    }

    override fun listField(descriptor: SdkFieldDescriptor, block: ListSerializer.() -> Unit) {
        xmlWriter.writeTagIf(descriptor.serialName.name, !descriptor.hasTrait<Flattened>()) {
            val s = XmlListSerializer(descriptor, xmlWriter, this@XmlSerializer)
            block(s)
        }
    }

    override fun mapField(descriptor: SdkFieldDescriptor, block: MapSerializer.() -> Unit) {
        serializeMap(descriptor, block)
    }

    override fun serializeNull() {
        // NOP
    }

    override fun serializeBoolean(value: Boolean) { xmlWriter.text(value.toString()) }

    override fun serializeByte(value: Byte) = serializeNumber(value)

    override fun serializeChar(value: Char) { xmlWriter.text(value.toString()) }

    override fun serializeShort(value: Short) = serializeNumber(value)

    override fun serializeInt(value: Int) = serializeNumber(value)

    override fun serializeLong(value: Long) = serializeNumber(value)

    override fun serializeFloat(value: Float) = serializeNumber(value)

    override fun serializeDouble(value: Double) = serializeNumber(value)

    private fun serializeNumber(value: Number) = xmlWriter.text(value)

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
    private val xmlSerializer: XmlSerializer
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

    override fun entry(key: String, value: SdkSerializable?) = generalEntry(key) {
        if (value == null) {
            xmlWriter.text("")
            return@generalEntry
        }

        xmlSerializer.parentDescriptorStack.push(descriptor)
        value.serialize(xmlSerializer)
        xmlSerializer.parentDescriptorStack.pop()
    }

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
        val tagName = descriptor.findTrait<XmlMapName>()?.value ?: XmlMapName.Default.value
        xmlWriter.writeTag(tagName)
    }

    private fun serializePrimitive(value: Any) {
        val tagName = descriptor.findTrait<XmlMapName>()?.value ?: XmlMapName.Default.value
        xmlWriter.writeTag(tagName) { value.toString() }
    }
}

private class XmlListSerializer(
    private val descriptor: SdkFieldDescriptor,
    private val xmlWriter: XmlStreamWriter,
    private val xmlSerializer: XmlSerializer
) : ListSerializer {

    private val tagName: String
        get() = when {
            descriptor.hasTrait<Flattened>() -> descriptor.serialName.name
            descriptor.hasTrait<XmlCollectionName>() -> descriptor.expectTrait<XmlCollectionName>().element
            else -> XmlCollectionName.Default.element
        }

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

    override fun serializeSdkSerializable(value: SdkSerializable) {
        xmlSerializer.parentDescriptorStack.push(descriptor)
        value.serialize(xmlSerializer)
        xmlSerializer.parentDescriptorStack.pop()
    }

    override fun serializeNull() {
        xmlWriter.writeTag(tagName)
    }

    override fun serializeRaw(value: String) = serializeString(value)

    private fun serializePrimitive(value: Any) {
        xmlWriter.writeTag(tagName) { text(value.toString()) }
    }
}

// FIXME - cleanup take a descriptor
/**
 * Write start tag, call [block] to fill contents, writes end tag
 */
private fun XmlStreamWriter.writeTag(name: String, block: XmlStreamWriter.() -> Unit = {}) {
    startTag(name)
    apply(block)
    endTag(name)
}

/**
 * Write the start/end tag only if [predicate] is true otherwise just call [block]
 * (useful if child content should be surrounded conditionally by a parent node)
 */
private fun XmlStreamWriter.writeTagIf(name: String, predicate: Boolean, block: XmlStreamWriter.() -> Unit = {}) {
    if (predicate) {
        writeTag(name, block)
    } else {
        apply(block)
    }
}

private val SdkFieldDescriptor.tagName: String
    get() = when {
        hasTrait<Flattened>() -> serialName.name
        hasTrait<XmlCollectionName>() -> expectTrait<XmlCollectionName>().element
        else -> when (kind) {
            SerialKind.List -> XmlCollectionName.Default.element
            SerialKind.Map -> XmlMapName.Default.entry!!
            else -> serialName.name
        }
    }
