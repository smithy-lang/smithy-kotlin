/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.serde.xml

import aws.smithy.kotlin.runtime.InternalApi
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
@InternalApi
public data class XmlMapName(
    public val entry: String? = Default.entry,
    public val key: String = Default.key,
    public val value: String = Default.value,
) : FieldTrait {
    @InternalApi
    public companion object {
        /**
         * The default serialized names for aspects of a Map in XML.
         * These defaults are specified here: https://awslabs.github.io/smithy/spec/xml.html#map-serialization
         */
        public val Default: XmlMapName = XmlMapName("entry", "key", "value")
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
@InternalApi
public data class XmlCollectionName(
    public val element: String,
) : FieldTrait {
    @InternalApi
    public companion object {
        /**
         * The default serialized name for a list or set element.
         * This default is specified here: https://awslabs.github.io/smithy/spec/xml.html#list-and-set-serialization
         */
        public val Default: XmlCollectionName = XmlCollectionName("member")
    }
}

/**
 * Denotes a collection type that uses a flattened XML representation
 * see: [xmlflattened trait](https://awslabs.github.io/smithy/1.0/spec/core/xml-traits.html#xmlflattened-trait)
 */
@InternalApi
public object Flattened : FieldTrait

/**
 * Specifies that an object is XML unwrapped response.
 *
 * Refer to: [s3 specific example](https://smithy.io/2.0/aws/customizations/s3-customizations.html#aws-customizations-s3unwrappedxmloutput-trait)
 */
@InternalApi
public object XmlUnwrappedOutput : FieldTrait

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
@InternalApi
public object XmlError : FieldTrait {
    public val errorTag: XmlToken.QualifiedName = XmlToken.QualifiedName("Error")
}

/**
 * Base class for more specific XML namespace traits
 */
@InternalApi
public open class AbstractXmlNamespaceTrait(public val uri: String, public val prefix: String? = null) {
    public fun isDefault(): Boolean = prefix == null
    override fun toString(): String = "AbstractXmlNamespace(uri=$uri, prefix=$prefix)"
}

/**
 * Describes the namespace associated with a field.
 * See https://awslabs.github.io/smithy/spec/xml.html#xmlnamespace-trait
 */
@InternalApi
public class XmlNamespace(uri: String, prefix: String? = null) : AbstractXmlNamespaceTrait(uri, prefix), FieldTrait

/**
 * Describes the namespace of a list or map's value element
 * Applies to [SerialKind.List] or [SerialKind.Map]
 */
@InternalApi
public class XmlCollectionValueNamespace(uri: String, prefix: String? = null) :
    AbstractXmlNamespaceTrait(uri, prefix), FieldTrait

/**
 * Describes the namespace associated with a map's key element
 * Applies to [SerialKind.Map]
 */
@InternalApi
public class XmlMapKeyNamespace(uri: String, prefix: String? = null) :
    AbstractXmlNamespaceTrait(uri, prefix), FieldTrait

/**
 * Specifies the name that a field is encoded into for Xml nodes.
 * See https://awslabs.github.io/smithy/1.0/spec/core/xml-traits.html?highlight=xmlname#xmlname-trait
 */
@InternalApi
public data class XmlSerialName(public val name: String) : FieldTrait

/**
 * Specifies an alternate name that can be used to match an XML node.
 */
@InternalApi
public data class XmlAliasName(public val name: String) : FieldTrait

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
    xmlNamespace: XmlNamespace? = findTrait<XmlNamespace>(),
): XmlToken.QualifiedName = toQualifiedName(xmlNamespace, findTrait<XmlSerialName>()?.name)

/**
 * Generate a set of qualified names from a field descriptor. The field descriptor must have trait [XmlSerialName]
 * otherwise an exception is thrown. Any additional names will be found from [XmlAliasName] traits.
 */
internal fun SdkFieldDescriptor.toQualifiedNames(
    xmlNamespace: XmlNamespace? = findTrait<XmlNamespace>(),
): Set<XmlToken.QualifiedName> =
    setOf(toQualifiedName()) +
        findTraits<XmlAliasName>().map { toQualifiedName(xmlNamespace, it.name) }

/**
 * Determines if the qualified name of this field descriptor matches the given name.
 */
internal fun SdkFieldDescriptor.nameMatches(other: String): Boolean = toQualifiedNames().any { it.toString() == other }

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
@InternalApi
public object XmlAttribute : FieldTrait

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
