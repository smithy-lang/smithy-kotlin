/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*

/**
 * Provides serialization for the XML message format.
 * @param xmlWriter where content is serialize to
 */
// TODO - mark class internal and remove integration tests once serde is stable
class XmlSerializer(private val xmlWriter: XmlStreamWriter = xmlStreamWriter()) : Serializer, StructSerializer {

    private var nodeStack = mutableListOf<XmlSerialName>()
    private var topLevel = true
    private var nestedDescriptor = false // tracks calls from field(descriptor: SdkFieldDescriptor, value: SdkSerializable)

    override fun toByteArray(): ByteArray {
        return xmlWriter.bytes
    }

    override fun beginStruct(descriptor: SdkFieldDescriptor): StructSerializer {
        // Serialize top-level ns declarations and non-default declarations.
        if (emitNamespaceDeclaration(descriptor, topLevel)) {
            descriptor.findTrait<XmlNamespace>()?.let { xmlNamespace ->
                xmlWriter.namespacePrefix(xmlNamespace.uri, xmlNamespace.prefix)
            }
        }
        topLevel = false

        if (descriptor.hasTrait<XmlSerialName>()) {
            if (nestedDescriptor) {
                val descriptor = nodeStack.last()
                xmlWriter.startTag(descriptor.name)
                nestedDescriptor = false
            } else {
                xmlWriter.startTag(descriptor.serialName.name)
                nodeStack.add(descriptor.serialName)
            }
        }

        return this
    }

    override fun beginList(descriptor: SdkFieldDescriptor): ListSerializer {
        if (!descriptor.hasTrait<Flattened>()) xmlWriter.startTag(descriptor.serialName.name)
        return XmlListSerializer(descriptor, xmlWriter, this)
    }

    override fun beginMap(descriptor: SdkFieldDescriptor): MapSerializer {
        if (!descriptor.hasTrait<Flattened>()) {
            xmlWriter.startTag(descriptor.serialName.name)
        }
        return XmlMapSerializer(descriptor, xmlWriter, this)
    }

    override fun endStruct(descriptor: SdkFieldDescriptor) {
        if (nodeStack.isNotEmpty() && descriptor.hasTrait<XmlSerialName>() && nodeStack.last().name == descriptor.serialName.name) {
            val lastTag = nodeStack.removeAt(nodeStack.size - 1)
            xmlWriter.endTag(lastTag.name)
        }
    }

    override fun field(descriptor: SdkFieldDescriptor, value: SdkSerializable) {
        // To ensure proper serialization, the descriptor instance here must be used in the call value.serialize()
        // In order to make this happen we set a boolean flag signaling this mode, and put the desired
        // field descriptor on the stack so it will be handled upon next call to beginStruct().
        nodeStack.add(descriptor.serialName)
        nestedDescriptor = true

        value.serialize(this)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Int) {
        xmlWriter.startTag(descriptor.serialName.name)
        serializeInt(value)
        xmlWriter.endTag(descriptor.serialName.name)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Long) {
        xmlWriter.startTag(descriptor.serialName.name)
        serializeLong(value)
        xmlWriter.endTag(descriptor.serialName.name)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Float) {
        xmlWriter.startTag(descriptor.serialName.name)
        serializeFloat(value)
        xmlWriter.endTag(descriptor.serialName.name)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: String) {
        if (descriptor.hasTrait<XmlAttribute>()) {
            xmlWriter.attribute(descriptor.serialName.name, value)
        } else {
            xmlWriter.startTag(descriptor)
            serializeString(value)
            xmlWriter.endTag(descriptor)
        }
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Double) {
        xmlWriter.startTag(descriptor.serialName.name)
        serializeDouble(value)
        xmlWriter.endTag(descriptor.serialName.name)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Boolean) {
        xmlWriter.startTag(descriptor.serialName.name)
        serializeBoolean(value)
        xmlWriter.endTag(descriptor.serialName.name)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Byte) {
        xmlWriter.startTag(descriptor.serialName.name)
        serializeByte(value)
        xmlWriter.endTag(descriptor.serialName.name)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Short) {
        xmlWriter.startTag(descriptor.serialName.name)
        serializeShort(value)
        xmlWriter.endTag(descriptor.serialName.name)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Char) {
        xmlWriter.startTag(descriptor.serialName.name)
        serializeChar(value)
        xmlWriter.endTag(descriptor.serialName.name)
    }

    override fun rawField(descriptor: SdkFieldDescriptor, value: String) = field(descriptor, value)

    override fun nullField(descriptor: SdkFieldDescriptor) {
        xmlWriter.startTag(descriptor.serialName.name)
        serializeNull()
        xmlWriter.endTag(descriptor.serialName.name)
    }

    override fun structField(descriptor: SdkFieldDescriptor, block: StructSerializer.() -> Unit) {
        serializeStruct(descriptor, block)
    }

    override fun listField(descriptor: SdkFieldDescriptor, block: ListSerializer.() -> Unit) {
        if (!descriptor.hasTrait<Flattened>()) xmlWriter.startTag(descriptor)

        val s = XmlListSerializer(descriptor, xmlWriter, this)
        block(s)

        if (!descriptor.hasTrait<Flattened>()) xmlWriter.endTag(descriptor)
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

// Write start tag and any necessary namespace declarations
private fun XmlStreamWriter.startTag(descriptor: SdkFieldDescriptor) {
    if (descriptor.hasTrait<XmlNamespace>()) {
        val ns = descriptor.expectTrait<XmlNamespace>()
        namespacePrefix(ns.uri, ns.prefix)
        val qualifiedName = descriptor.toQualifiedName(ns)

        startTag(qualifiedName.local, qualifiedName.prefix)
    } else {
        startTag(descriptor.serialName.name)
    }
}

// Write end tag
private fun XmlStreamWriter.endTag(descriptor: SdkFieldDescriptor) {
    if (descriptor.hasTrait<XmlNamespace>()) {
        val ns = descriptor.expectTrait<XmlNamespace>()
        val qualifiedName = descriptor.toQualifiedName(ns)

        endTag(qualifiedName.local, qualifiedName.prefix)
    } else {
        endTag(descriptor.serialName.name)
    }
}

// Determines if a namespace declaration should emitted on a specific tag
private fun emitNamespaceDeclaration(descriptor: SdkFieldDescriptor, topLevel: Boolean): Boolean =
    descriptor.hasTrait<XmlNamespace>() && (!descriptor.expectTrait<XmlNamespace>().isDefault() || topLevel)

private class XmlMapSerializer(
    private val descriptor: SdkFieldDescriptor,
    private val xmlWriter: XmlStreamWriter,
    private val xmlSerializer: Serializer
) : MapSerializer {

    override fun endMap() {
        if (!descriptor.hasTrait<Flattened>()) {
            xmlWriter.endTag(descriptor.serialName.name)
        }
    }

    fun generalEntry(key: String, valueFn: () -> Unit) {
        val mapTrait = descriptor.findTrait() ?: XmlMapName.Default

        if (!descriptor.hasTrait<Flattened>()) {
            xmlWriter.startTag(mapTrait.entry!!)
        } else {
            xmlWriter.startTag(descriptor.serialName.name)
        }
        xmlWriter.startTag(mapTrait.key)
        xmlWriter.text(key)
        xmlWriter.endTag(mapTrait.key)
        xmlWriter.startTag(mapTrait.value)
        valueFn()
        xmlWriter.endTag(mapTrait.value)

        if (!descriptor.hasTrait<Flattened>()) {
            xmlWriter.endTag(mapTrait.entry!!)
        } else {
            xmlWriter.endTag(descriptor.serialName.name)
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
        if (!descriptor.hasTrait<Flattened>()) xmlWriter.endTag(descriptor.serialName.name)
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
        val nodeName = when {
            descriptor.hasTrait<Flattened>() -> descriptor.serialName.name
            descriptor.hasTrait<XmlCollectionName>() -> descriptor.expectTrait<XmlCollectionName>().element
            else -> XmlCollectionName.Default.element
        }

        xmlWriter.startTag(nodeName)
        xmlWriter.endTag(nodeName)
    }

    override fun serializeRaw(value: String) = serializeString(value)

    private fun serializePrimitive(value: Any) {
        val nodeName = when {
            descriptor.hasTrait<Flattened>() -> descriptor.serialName.name
            descriptor.hasTrait<XmlCollectionName>() -> descriptor.expectTrait<XmlCollectionName>().element
            else -> XmlCollectionName.Default.element
        }
        if (descriptor.hasTrait<XmlCollectionNamespace>()) {
            val ns = descriptor.expectTrait<XmlCollectionNamespace>()
            xmlWriter.namespacePrefix(ns.uri, ns.prefix)
        }

        xmlWriter.startTag(nodeName)
        xmlWriter.text(value.toString())
        xmlWriter.endTag(nodeName)
    }
}
