/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*

// NOTE: By default, a descriptor without any Xml trait is assumed to be a primitive TEXT value.

/**
 * Specifies entry, key, and value node names used to encode a Map structure.
 * See https://awslabs.github.io/smithy/spec/xml.html#map-serialization
 *
 * This trait need only be added to a [SdkFieldDescriptor] if the map entry, key, or value is something
 * other than the default specified in [XmlMapName.DEFAULT]
 *
 * @param entry the name of the entry node which wraps map entries. Must be null for flat maps.
 * @param key the name of the key field
 * @param value the name of the value field
 */
data class XmlMapName(
    val entry: String? = DEFAULT.entry,
    val key: String = DEFAULT.key,
    val value: String = DEFAULT.value
) : FieldTrait {
    companion object {
        /**
         * The default serialized names for aspects of a Map in XML.
         * These defaults are specified here: https://awslabs.github.io/smithy/spec/xml.html#map-serialization
         */
        val DEFAULT = XmlMapName("entry", "key", "value")
    }
}

/**
 * Specifies element wrapper name used to encode a List structure.
 * See https://awslabs.github.io/smithy/spec/xml.html#list-and-set-serialization
 *
 * This trait need only be added to a [SdkFieldDescriptor] if the element name is something
 * other than the default specified in [XmlCollectionName.DEFAULT]
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
        val DEFAULT = XmlCollectionName("member")
    }
}

/*
 * Denotes a collection type that uses a flattened XML representation
 * see: [xmlflattened trait](https://awslabs.github.io/smithy/1.0/spec/core/xml-traits.html#xmlflattened-trait)
 */
object Flattened : FieldTrait

/**
 * Describes the namespace associated with a field.
 * See https://awslabs.github.io/smithy/spec/xml.html#xmlnamespace-trait
 */
data class XmlNamespace(val uri: String, val prefix: String? = null) : FieldTrait {
    fun isDefault() = prefix == null
}

/**
 * Specifies a namespace that a field is encoded into for Xml nodes.
 */
data class XmlSerialName(val name: String) : FieldTrait {
    fun toQualifiedName(xmlNamespace: XmlNamespace? = null): XmlToken.QualifiedName =
        when {
            xmlNamespace != null -> {
                val (nodeName, prefix) = name.parseNodeWithPrefix()

                when (prefix) {
                    xmlNamespace.prefix -> XmlToken.QualifiedName(
                        nodeName,
                        xmlNamespace.uri,
                        xmlNamespace.prefix
                    )
                    else -> XmlToken.QualifiedName(name)
                }
            }
            name.nodeHasPrefix() -> {
                val (nodeName, prefix) = name.parseNodeWithPrefix()
                XmlToken.QualifiedName(nodeName, null, prefix)
            }
            else -> XmlToken.QualifiedName(name)
        }
}

/**
 * This predicate relies on the ability in Smithy
 * to specify a namespace as part of the name:
 * https://awslabs.github.io/smithy/spec/xml.html#xmlname-trait
 */
fun String.nodeHasPrefix(): Boolean = this.contains(':')

/**
 * Return none name and namespace as a pair or just the name and null namespace
 * if no namespace is defined.
 */
fun String.parseNodeWithPrefix(): Pair<String, String?> =
    if (this.nodeHasPrefix()) {
        val (namespacePrefix, name) = this.split(':')
        name to namespacePrefix
    } else {
        this to null
    }

/**
 * Specifies that a field is encoded into an XML attribute and describes the XML.
 * See https://awslabs.github.io/smithy/spec/xml.html#xmlattribute-trait
 *
 * @param name the name of the attribute
 * @param namespace the namespace of the attribute, or null for none.
 */
data class XmlAttribute(val name: String, val namespace: String? = null) : FieldTrait

/**
 * Provides the serialized name of the field.
 */
val SdkFieldDescriptor.serialName: XmlSerialName
    get() = expectTrait()

// Return the name based on most specific type
internal fun SdkFieldDescriptor.generalName() = when {
    hasTrait<XmlCollectionName>() -> findTrait<XmlCollectionName>()?.element ?: XmlCollectionName.DEFAULT.element
    hasTrait<XmlMapName>() -> findTrait<XmlMapName>()?.value ?: XmlMapName.DEFAULT.value
    else -> expectTrait<XmlSerialName>().name
}
