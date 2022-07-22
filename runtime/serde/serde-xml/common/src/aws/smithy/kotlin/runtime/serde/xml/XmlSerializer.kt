/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde.xml

import aws.smithy.kotlin.runtime.serde.*
import aws.smithy.kotlin.runtime.smithy.Document
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.TimestampFormat
import aws.smithy.kotlin.runtime.util.*

/**
 * Provides serialization for the XML message format.
 * @param xmlWriter where content is serialize to
 */
// TODO - mark class internal and remove integration tests once serde is stable
public class XmlSerializer(private val xmlWriter: XmlStreamWriter = xmlStreamWriter()) : Serializer, StructSerializer {

    // FIXME - clean up stack to distinguish between mutable/immutable and move to utils? (e.g. MutableStack<T> = mutableStackOf())
    private var nodeStack: ListStack<String> = mutableListOf()
    internal var parentDescriptorStack: ListStack<SdkFieldDescriptor> = mutableListOf()

    override fun toByteArray(): ByteArray = xmlWriter.bytes

    override fun beginStruct(descriptor: SdkFieldDescriptor): StructSerializer {
        // if we are serializing a nested structure field
        // either through `field(.., SdkSerializable)` or as part of a list member/map entry
        // use the parent descriptor instead of the object descriptor passed to us.
        // The object descriptor is for root nodes, nested structures have their own field descriptor
        // that describes the referred to struct
        val structDescriptor = parentDescriptorStack.topOrNull() ?: descriptor

        // Serialize top-level (root node) ns declarations and non-default declarations.
        val isRoot = nodeStack.isEmpty()
        val ns = structDescriptor.findTrait<XmlNamespace>()
        if (ns != null && (isRoot || ns.prefix != null)) {
            xmlWriter.namespacePrefix(ns.uri, ns.prefix)
        }

        val tagName = structDescriptor.serialName.name

        // if the parent descriptor is from a list or map we omit the root level node
        // e.g. Map<String, GreetingStruct> goes to:
        // `<value><hi>foo</hi></value>`
        // instead of
        // `<value><GreetingStruct><hi>foo</hi></GreetingStruct></value>`
        //
        if (!structDescriptor.isMapOrList) {
            xmlWriter.startTag(tagName)
        }

        nodeStack.push(tagName)

        return this
    }

    override fun beginList(descriptor: SdkFieldDescriptor): ListSerializer {
        if (!descriptor.hasTrait<Flattened>()) {
            val ns = descriptor.findTrait<XmlNamespace>()
            xmlWriter.startTag(descriptor.serialName.name, ns)
        }
        return XmlListSerializer(descriptor, xmlWriter, this)
    }

    override fun beginMap(descriptor: SdkFieldDescriptor): MapSerializer {
        if (!descriptor.hasTrait<Flattened>()) {
            val ns = descriptor.findTrait<XmlNamespace>()
            xmlWriter.startTag(descriptor.serialName.name, ns)
        }
        return XmlMapSerializer(descriptor, xmlWriter, this)
    }

    override fun endStruct() {
        check(nodeStack.isNotEmpty()) { "Expected nodeStack to have a value, but was empty." }
        val tagName = nodeStack.pop()

        if (parentDescriptorStack.isNotEmpty() && !parentDescriptorStack.top().isMapOrList) {
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
        val ns = descriptor.findTrait<XmlNamespace>()
        when {
            descriptor.hasTrait<XmlAttribute>() -> xmlWriter.attribute(descriptor.serialName.name, value.toString(), ns?.uri)
            else -> xmlWriter.writeTag(descriptor.serialName.name, ns) { serdeFn(value) }
        }
    }

    private fun numberField(descriptor: SdkFieldDescriptor, value: Number) =
        tagOrAttribute(descriptor, value, ::serializeNumber)

    override fun field(descriptor: SdkFieldDescriptor, value: Boolean): Unit =
        tagOrAttribute(descriptor, value, ::serializeBoolean)

    override fun field(descriptor: SdkFieldDescriptor, value: Byte): Unit = numberField(descriptor, value)

    override fun field(descriptor: SdkFieldDescriptor, value: Char): Unit =
        tagOrAttribute(descriptor, value, ::serializeChar)

    override fun field(descriptor: SdkFieldDescriptor, value: Short): Unit = numberField(descriptor, value)

    override fun field(descriptor: SdkFieldDescriptor, value: Int): Unit = numberField(descriptor, value)

    override fun field(descriptor: SdkFieldDescriptor, value: Long): Unit = numberField(descriptor, value)

    override fun field(descriptor: SdkFieldDescriptor, value: Float): Unit = numberField(descriptor, value)

    override fun field(descriptor: SdkFieldDescriptor, value: Double): Unit = numberField(descriptor, value)

    override fun field(descriptor: SdkFieldDescriptor, value: String): Unit =
        tagOrAttribute(descriptor, value, ::serializeString)

    override fun field(descriptor: SdkFieldDescriptor, value: Instant, format: TimestampFormat): Unit =
        field(descriptor, value.format(format))

    override fun field(descriptor: SdkFieldDescriptor, value: Document?) {
        throw SerializationException(
            "cannot serialize field ${descriptor.serialName}; Document type is not supported by xml encoding"
        )
    }

    override fun nullField(descriptor: SdkFieldDescriptor) {
        xmlWriter.writeTag(descriptor.serialName.name) {
            serializeNull()
        }
    }

    override fun structField(descriptor: SdkFieldDescriptor, block: StructSerializer.() -> Unit) {
        serializeStruct(descriptor, block)
    }

    override fun listField(descriptor: SdkFieldDescriptor, block: ListSerializer.() -> Unit) {
        serializeList(descriptor, block)
    }

    override fun mapField(descriptor: SdkFieldDescriptor, block: MapSerializer.() -> Unit) {
        serializeMap(descriptor, block)
    }

    override fun serializeNull() {
        // NOP
    }

    override fun serializeDocument(value: Document?) {
        throw SerializationException("document values not supported by xml serializer")
    }

    override fun serializeBoolean(value: Boolean) { xmlWriter.text(value.toString()) }

    override fun serializeByte(value: Byte): Unit = serializeNumber(value)

    override fun serializeChar(value: Char) { xmlWriter.text(value.toString()) }

    override fun serializeShort(value: Short): Unit = serializeNumber(value)

    override fun serializeInt(value: Int): Unit = serializeNumber(value)

    override fun serializeLong(value: Long): Unit = serializeNumber(value)

    override fun serializeFloat(value: Float): Unit = serializeNumber(value)

    override fun serializeDouble(value: Double): Unit = serializeNumber(value)

    private fun serializeNumber(value: Number): Unit = xmlWriter.text(value)

    override fun serializeString(value: String) {
        xmlWriter.text(value)
    }

    override fun serializeInstant(value: Instant, format: TimestampFormat) {
        xmlWriter.text(value.format(format))
    }

    override fun serializeSdkSerializable(value: SdkSerializable): Unit = value.serialize(this)
}

private class XmlMapSerializer(
    private val descriptor: SdkFieldDescriptor,
    private val xmlWriter: XmlStreamWriter,
    private val xmlSerializer: XmlSerializer,
    private val nestedMap: Boolean = false,
) : MapSerializer {

    override fun endMap() {
        if (!descriptor.hasTrait<Flattened>() && !nestedMap) {
            xmlWriter.endTag(descriptor.serialName.name)
        }
    }

    private fun writeEntry(key: String, valueFn: () -> Unit) {
        val mapTrait = descriptor.findTrait() ?: XmlMapName.Default

        val tagName = if (descriptor.hasTrait<Flattened>()) {
            descriptor.serialName.name
        } else {
            checkNotNull(mapTrait.entry)
        }

        val entryNamespace = descriptor.findTrait<XmlNamespace>()
        val keyNamespace = descriptor.findTrait<XmlMapKeyNamespace>()
        val valueNamespace = descriptor.findTrait<XmlCollectionValueNamespace>()

        xmlWriter.writeTag(tagName, entryNamespace) {
            writeTag(mapTrait.key, keyNamespace) { text(key) }
            writeTag(mapTrait.value, valueNamespace) { valueFn() }
        }
    }

    override fun entry(key: String, value: Int?): Unit = writeEntry(key) { xmlWriter.text(value.toString()) }

    override fun entry(key: String, value: Long?): Unit = writeEntry(key) { xmlWriter.text(value.toString()) }

    override fun entry(key: String, value: Float?): Unit = writeEntry(key) { xmlWriter.text(value.toString()) }

    override fun entry(key: String, value: String?): Unit = writeEntry(key) { xmlWriter.text(value ?: "") }

    override fun entry(key: String, value: SdkSerializable?): Unit = writeEntry(key) {
        if (value == null) {
            xmlWriter.text("")
            return@writeEntry
        }

        xmlSerializer.parentDescriptorStack.push(descriptor)
        value.serialize(xmlSerializer)
        xmlSerializer.parentDescriptorStack.pop()
    }

    override fun entry(key: String, value: Double?): Unit = writeEntry(key) { xmlWriter.text(value.toString()) }

    override fun entry(key: String, value: Boolean?): Unit = writeEntry(key) { xmlWriter.text(value.toString()) }

    override fun entry(key: String, value: Byte?): Unit = writeEntry(key) { xmlWriter.text(value.toString()) }

    override fun entry(key: String, value: Short?): Unit = writeEntry(key) { xmlWriter.text(value.toString()) }

    override fun entry(key: String, value: Char?): Unit = writeEntry(key) { xmlWriter.text(value.toString()) }

    override fun entry(key: String, value: Instant?, format: TimestampFormat): Unit = entry(key, value?.format(format))

    override fun listEntry(key: String, listDescriptor: SdkFieldDescriptor, block: ListSerializer.() -> Unit) {
        writeEntry(key) {
            val ls = xmlSerializer.beginList(listDescriptor)
            block.invoke(ls)
            ls.endList()
        }
    }

    override fun mapEntry(key: String, mapDescriptor: SdkFieldDescriptor, block: MapSerializer.() -> Unit) {
        writeEntry(key) {
            // nested maps do not require the surrounding member tag and Flattened only applies to structure members
            // instead the child map's entries are serialized directly into the <value> tag of the parent map's entry
            val ms = XmlMapSerializer(mapDescriptor, xmlWriter, xmlSerializer, nestedMap = true)
            block.invoke(ms)
        }
    }

    override fun serializeBoolean(value: Boolean): Unit = serializePrimitive(value)

    override fun serializeByte(value: Byte): Unit = serializePrimitive(value)

    override fun serializeShort(value: Short): Unit = serializePrimitive(value)

    override fun serializeChar(value: Char): Unit = serializePrimitive(value)

    override fun serializeInt(value: Int): Unit = serializePrimitive(value)

    override fun serializeLong(value: Long): Unit = serializePrimitive(value)

    override fun serializeFloat(value: Float): Unit = serializePrimitive(value)

    override fun serializeDouble(value: Double): Unit = serializePrimitive(value)

    override fun serializeString(value: String): Unit = serializePrimitive(value)

    override fun serializeSdkSerializable(value: SdkSerializable): Unit = value.serialize(xmlSerializer)

    override fun serializeInstant(value: Instant, format: TimestampFormat): Unit = serializeString(value.format(format))

    override fun serializeNull() {
        val tagName = descriptor.findTrait<XmlMapName>()?.value ?: XmlMapName.Default.value
        val ns = descriptor.findTrait<XmlCollectionValueNamespace>()
        xmlWriter.writeTag(tagName, ns)
    }

    override fun serializeDocument(value: Document?) {
        throw SerializationException("document values not supported by xml serializer")
    }

    private fun serializePrimitive(value: Any) {
        val tagName = descriptor.findTrait<XmlMapName>()?.value ?: XmlMapName.Default.value
        val ns = descriptor.findTrait<XmlCollectionValueNamespace>()
        xmlWriter.writeTag(tagName, ns) { value.toString() }
    }
}

private class XmlListSerializer(
    // the list/collection descriptor
    private val descriptor: SdkFieldDescriptor,
    private val xmlWriter: XmlStreamWriter,
    private val xmlSerializer: XmlSerializer
) : ListSerializer {

    override fun endList() {
        if (!descriptor.hasTrait<Flattened>()) {
            xmlWriter.endTag(descriptor.serialName.name)
        }
    }

    private val memberTagName: String
        get() = when {
            descriptor.hasTrait<Flattened>() -> descriptor.serialName.name
            else -> descriptor.findTrait<XmlCollectionName>()?.element ?: XmlCollectionName.Default.element
        }

    override fun serializeBoolean(value: Boolean): Unit = serializePrimitive(value)

    override fun serializeByte(value: Byte): Unit = serializePrimitive(value)

    override fun serializeShort(value: Short): Unit = serializePrimitive(value)

    override fun serializeChar(value: Char): Unit = serializePrimitive(value)

    override fun serializeInt(value: Int): Unit = serializePrimitive(value)

    override fun serializeLong(value: Long): Unit = serializePrimitive(value)

    override fun serializeFloat(value: Float): Unit = serializePrimitive(value)

    override fun serializeDouble(value: Double): Unit = serializePrimitive(value)

    override fun serializeString(value: String): Unit = serializePrimitive(value)

    override fun serializeSdkSerializable(value: SdkSerializable) {
        xmlSerializer.parentDescriptorStack.push(descriptor)
        val ns = descriptor.findTrait<XmlCollectionValueNamespace>()
        xmlWriter.writeTag(memberTagName, ns) {
            value.serialize(xmlSerializer)
        }
        xmlSerializer.parentDescriptorStack.pop()
    }

    override fun serializeNull() {
        val ns = descriptor.findTrait<XmlCollectionValueNamespace>()
        xmlWriter.writeTag(memberTagName, ns)
    }

    override fun serializeDocument(value: Document?) {
        throw SerializationException("document values not supported by xml serializer")
    }

    override fun serializeInstant(value: Instant, format: TimestampFormat): Unit = serializeString(value.format(format))

    private fun serializePrimitive(value: Any) {
        val ns = descriptor.findTrait<XmlCollectionValueNamespace>()
        xmlWriter.writeTag(memberTagName, ns) { text(value.toString()) }
    }
}

/**
 * Write start tag, call [block] to fill contents, writes end tag
 */
private fun XmlStreamWriter.writeTag(
    tagName: String,
    ns: AbstractXmlNamespaceTrait? = null,
    block: XmlStreamWriter.() -> Unit = {}
) {
    startTag(tagName, ns)
    apply(block)
    endTag(tagName)
}

private fun XmlStreamWriter.startTag(tagName: String, ns: AbstractXmlNamespaceTrait?) {
    if (ns != null) {
        namespacePrefix(ns.uri, ns.prefix)
    }
    startTag(tagName)
}

/**
 * Return true if the descriptor represents a list or map type, false otherwise
 */
private val SdkFieldDescriptor.isMapOrList: Boolean
    get() = kind == SerialKind.List || kind == SerialKind.Map
