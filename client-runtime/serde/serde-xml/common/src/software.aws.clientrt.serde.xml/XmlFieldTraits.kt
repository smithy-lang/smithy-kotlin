/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.FieldTrait

// TODO: The XML specific Traits which describe names will need to be amended to include namespace (or a Qualified Name)

// NOTE: By default, a descriptor without any Xml trait is assumed to be a primitive TEXT value.

/**
 * Specifies that a field represents a Map structure and describes the XML node names used to encode that structure.
 * See https://awslabs.github.io/smithy/spec/xml.html#map-serialization
 *
 * @param entry the name of the entry node which wraps map entries.
 * @param keyName the name of the key field
 * @param valueName the name of the value field
 * @param flattened determines of the map has a flattened structure.  See https://awslabs.github.io/smithy/spec/xml.html#flattened-map-serialization
 */
data class XmlMap(
    val entry: String = "entry",
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
    val elementName: String = "element"
) : FieldTrait

/**
 * Specifies that a field is encoded into an XML attribute and describes the XML.
 * See https://awslabs.github.io/smithy/spec/xml.html#xmlattribute-trait
 *
 * @param name the name of the attribute
 * @param namespace the namespace of the attribute, or null for none.
 */
data class XmlAttribute(val name: String, val namespace: String? = null) : FieldTrait
