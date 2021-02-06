/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.FieldTrait
import software.aws.clientrt.serde.SdkFieldDescriptor

// TODO: The XML specific Traits which describe names will need to be amended to include namespace (or a Qualified Name)

// NOTE: By default, a descriptor without any Xml trait is assumed to be a primitive TEXT value.

/**
 * Specifies that a field represents a Map structure and describes the XML node names used to encode that structure.
 * See https://awslabs.github.io/smithy/spec/xml.html#map-serialization
 *
 * @param entry the name of the entry node which wraps map entries. Should be null for flat maps.
 * @param keyName the name of the key field
 * @param valueName the name of the value field
 * @param flattened determines of the map has a flattened structure.  See https://awslabs.github.io/smithy/spec/xml.html#flattened-map-serialization
 */
data class XmlMap(
    val entry: String? = "entry",
    val keyName: String = "key",
    val valueName: String = "value",
    val flattened: Boolean = false
) : FieldTrait

/**
 * Specifies that a field represents a List structure and the XML node names used to encode that structure.
 * See https://awslabs.github.io/smithy/spec/xml.html#list-and-set-serialization
 *
 * @param elementName the name of the XML node which wraps each list entry.
 */
data class XmlList(
    val elementName: String = "element",
    val flattened: Boolean = false
) : FieldTrait

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

                if (xmlNamespace.prefix == prefix) XmlToken.QualifiedName(nodeName, xmlNamespace.uri, xmlNamespace.prefix) else XmlToken.QualifiedName(name)
            }
            name.nodeHasPrefix() -> {
                val (nodeName, prefix) = name.parseNodeWithPrefix()
                XmlToken.QualifiedName(nodeName, null, prefix)
            }
            else -> XmlToken.QualifiedName(name)
        }
}

fun String.nodeHasPrefix(): Boolean = this.contains(':')

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

val SdkFieldDescriptor.serialName: XmlSerialName
    get() = expectTrait()

// Return the name based on most specific type
internal fun SdkFieldDescriptor.generalName() = when {
    hasTrait<XmlList>() -> expectTrait<XmlList>().elementName
    hasTrait<XmlMap>() -> expectTrait<XmlMap>().valueName
    else -> expectTrait<XmlSerialName>().name
}
