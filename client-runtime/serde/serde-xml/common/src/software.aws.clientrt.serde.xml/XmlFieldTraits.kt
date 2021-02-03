/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.FieldTrait
import software.aws.clientrt.serde.SdkFieldDescriptor
import software.aws.clientrt.serde.SdkObjectDescriptor
import software.aws.clientrt.serde.SerialKind

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
): FieldTrait

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
data class XmlSerialName(val name: String): FieldTrait {
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
data class XmlAttribute(val name: String, val namespace: String? = null): FieldTrait

/*fun SdkFieldDescriptor(name: String, kind: SerialKind, index: Int = 0, trait: FieldTrait? = null): SdkFieldDescriptor {
    val xmlSerialName = XmlSerialName(name)

    return if (trait != null)
        SdkFieldDescriptor(kind = kind, index = index, traits = setOf(xmlSerialName, trait))
    else
        SdkFieldDescriptor(kind = kind, index = index, traits = setOf(xmlSerialName))
}*/

val SdkFieldDescriptor.serialName: XmlSerialName
    get() = expectTrait()

/*
fun SdkFieldDescriptor(name: String, kind: SerialKind, namespaceUri: String? = null, namespacePrefix: String? = null, index: Int = 0, trait: FieldTrait? = null): SdkFieldDescriptor {
    val xmlSerialName = if (namespaceUri != null) {
        XmlSerialName(name, namespacePrefix)
    } else {
        XmlSerialName(name)
    }

    return if (trait != null)
        SdkFieldDescriptor(kind = kind, index = index, traits = setOf(xmlSerialName, trait))
    else
        SdkFieldDescriptor(kind = kind, index = index, traits = setOf(xmlSerialName))
}

val SdkFieldDescriptor.serialName: XmlSerialName
    get() = expectTrait()

var SdkObjectDescriptor.DslBuilder.serialName
    get(): String = error { "Should not be called" }
    set(value) {
        check(!traits.any { it is XmlSerialName }) { "Serial name cannot be set multiple times" }
        val trait = if (value.contains(':')) {
            val (prefix, name) = value.split(':')
            XmlSerialName(name, prefix)
        } else {
            XmlSerialName(value)
        }
        traits.add(trait)
    }

var SdkObjectDescriptor.DslBuilder.namespace
    get(): XmlNamespace = error { "Should not be called" }
    set(value) {
        traits.add(value)
    }*/
