/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.serde.xml

import aws.smithy.kotlin.runtime.serde.*

// NOTE: By default, a descriptor without any Xml trait is assumed to be a primitive TEXT value.

/**
 * Specifies entry, key, and value node names used to encode a Map structure.
 * See https://awslabs.github.io/smithy/spec/xml.html#map-serialization
 *
 * This trait need only be added to a [SdkFieldDescriptor] if the map entry, key, or value is something
 * other than the default specified in [XmlMapName.Default]
 *
 * @param entry the name of the entry node which wraps map entries. Must be null for flat maps.
 * @param key the name of the key field
 * @param value the name of the value field
 */
data class XmlMapName(
    val entry: String? = Default.entry,
    val key: String = Default.key,
    val value: String = Default.value
) : FieldTrait {
    companion object {
        /**
         * The default serialized names for aspects of a Map in XML.
         * These defaults are specified here: https://awslabs.github.io/smithy/spec/xml.html#map-serialization
         */
        val Default = XmlMapName("entry", "key", "value")
    }
}

/**
 * Specifies element wrapper name used to encode a List structure.
 * See https://awslabs.github.io/smithy/spec/xml.html#list-and-set-serialization
 *
 * This trait need only be added to a [SdkFieldDescriptor] if the element name is something
 * other than the default specified in [XmlCollectionName.Default]
 *
 * @param element the name of the XML node which wraps each list or set entry.
 */
data class XmlCollectionName(
    val element: String
) : FieldTrait {
    companion object {
        /**
         * The default serialized name for a list or set element.
         * This default is specified here: https://awslabs.github.io/smithy/spec/xml.html#list-and-set-serialization
         */
        val Default = XmlCollectionName("member")
    }
}

/**
 * Denotes a collection type that uses a flattened XML representation
 * see: [xmlflattened trait](https://awslabs.github.io/smithy/1.0/spec/core/xml-traits.html#xmlflattened-trait)
 */
object Flattened : FieldTrait

/**
 * Denotes a structure that represents an error.  There are special rules for error deserialization
 * in various XML-based protocols. This trait provides necessary context to the deserializer to properly
 * deserialize error response data into types.
 *
 * See https://awslabs.github.io/smithy/1.0/spec/aws/aws-restxml-protocol.html#operation-error-serialization
 *
 * NOTE/FIXME: This type was written to handle the restXml protocol handling but could be refactored to be more
 *       general purpose if/when necessary to support other XML-based protocols.
 */
object XmlError : FieldTrait {
    val errorTag: XmlToken.QualifiedName = XmlToken.QualifiedName("Error")
}

/**
 * Base class for more specific XML namespace traits
 */
open class AbstractXmlNamespaceTrait(val uri: String, val prefix: String? = null) {
    fun isDefault() = prefix == null
    override fun toString(): String = "AbstractXmlNamespace(uri=$uri, prefix=$prefix)"
}

/**
 * Describes the namespace associated with a field.
 * See https://awslabs.github.io/smithy/spec/xml.html#xmlnamespace-trait
 */
class XmlNamespace(uri: String, prefix: String? = null) : AbstractXmlNamespaceTrait(uri, prefix), FieldTrait

/**
 * Describes the namespace of a list or map's value element
 * Applies to [SerialKind.List] or [SerialKind.Map]
 */
class XmlCollectionValueNamespace(uri: String, prefix: String? = null) : AbstractXmlNamespaceTrait(uri, prefix), FieldTrait

/**
 * Describes the namespace associated with a map's key element
 * Applies to [SerialKind.Map]
 */
class XmlMapKeyNamespace(uri: String, prefix: String? = null) : AbstractXmlNamespaceTrait(uri, prefix), FieldTrait

/**
 * Specifies the name that a field is encoded into for Xml nodes.
 * See https://awslabs.github.io/smithy/1.0/spec/core/xml-traits.html?highlight=xmlname#xmlname-trait
 */
data class XmlSerialName(val name: String) : FieldTrait

/**
 * Specifies an alternate name that can be used to match an XML node.
 */
data class XmlAliasName(val name: String) : FieldTrait

private fun toQualifiedName(xmlNamespace: XmlNamespace?, name: String?): XmlToken.QualifiedName {
    val (localName, prefix) = name
        ?.parseNodeWithPrefix()
        ?: throw DeserializationException("Unable to parse qualified name from $name")

    return when {
        xmlNamespace != null -> XmlToken.QualifiedName(localName, if (prefix == xmlNamespace.prefix) prefix else null)
        prefix != null -> XmlToken.QualifiedName(localName, prefix)
        else -> XmlToken.QualifiedName(localName)
    }
}

/**
 * Generate a qualified name from a field descriptor. The field descriptor must have trait [XmlSerialName] otherwise an
 * exception is thrown.
 */
internal fun SdkFieldDescriptor.toQualifiedName(
    xmlNamespace: XmlNamespace? = findTrait<XmlNamespace>()
): XmlToken.QualifiedName = toQualifiedName(xmlNamespace, findTrait<XmlSerialName>()?.name)

/**
 * Generate a set of qualified names from a field descriptor. The field descriptor must have trait [XmlSerialName]
 * otherwise an exception is thrown. Any additional names will be found from [XmlAliasName] traits.
 */
internal fun SdkFieldDescriptor.toQualifiedNames(
    xmlNamespace: XmlNamespace? = findTrait<XmlNamespace>()
): Set<XmlToken.QualifiedName> =
    setOf(toQualifiedName()) +
        findTraits<XmlAliasName>().map { toQualifiedName(xmlNamespace, it.name) }

/**
 * Determines if the qualified name of this field descriptor matches the given name.
 */
internal fun SdkFieldDescriptor.nameMatches(other: String): Boolean = toQualifiedNames().any { it.tag == other }

/**
 * Requires that the given name matches one of this field descriptor's qualified names.
 */
internal fun SdkFieldDescriptor.requireNameMatch(other: String) {
    val qualifiedNames = toQualifiedNames()
    if (!nameMatches(other)) {
        val validNames = qualifiedNames.joinToString(" or ")
        val error = "Expected beginning element named $validNames but found $other"
        throw DeserializationException(error)
    }
}

/**
 * This predicate relies on the ability in Smithy
 * to specify a namespace as part of the name:
 * https://awslabs.github.io/smithy/spec/xml.html#xmlname-trait
 */
internal fun String.nodeHasPrefix(): Boolean = this.contains(':')

/**
 * Return none name and namespace as a pair or just the name and null namespace
 * if no namespace is defined.
 */
internal fun String.parseNodeWithPrefix(): Pair<String, String?> =
    if (this.nodeHasPrefix()) {
        val (namespacePrefix, name) = this.split(':')
        name to namespacePrefix
    } else {
        this to null
    }

/**
 * Specifies that a field is encoded into an XML attribute and describes the XML.
 * See https://awslabs.github.io/smithy/spec/xml.html#xmlattribute-trait
 */
object XmlAttribute : FieldTrait

/**
 * Provides the serialized name of the field.
 */
internal val SdkFieldDescriptor.serialName: XmlSerialName
    get() = expectTrait()

// Return the name based on most specific type
internal fun SdkFieldDescriptor.generalName() = when {
    hasTrait<XmlCollectionName>() -> findTrait<XmlCollectionName>()?.element ?: XmlCollectionName.Default.element
    hasTrait<XmlMapName>() -> findTrait<XmlMapName>()?.value ?: XmlMapName.Default.value
    else -> expectTrait<XmlSerialName>().name
}

// Returns true if any fields directly associated to object descriptor are attributes
internal val SdkObjectDescriptor.hasXmlAttributes: Boolean
    get() = fields.any { it.hasTrait<XmlAttribute>() }
