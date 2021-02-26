/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.serde.xml

/**
 * Raw tokens produced when reading a XML document as a stream
 */
sealed class XmlToken {

    /**
     * An namespace declaration (xmlns)
     */
    data class Namespace(val uri: String, val prefix: String? = null)

    /**
     * Defines the name and namespace of an element
     */
    data class QualifiedName(val name: String, val namespaceUri: String? = null, val namespacePrefix: String? = null)

    /**
     * The opening of an XML element
     */
    data class BeginElement(
        val qualifiedName: QualifiedName,
        val attributes: Map<QualifiedName, String> = emptyMap(),
        val nsDeclarations: List<Namespace> = emptyList()
    ) : XmlToken() {
        // Convenience constructor for name-only nodes.
        constructor(name: String) : this(QualifiedName(name))
        // Convenience constructor for name-only nodes with attributes.
        constructor(name: String, attributes: Map<QualifiedName, String>) : this(QualifiedName(name), attributes)
    }

    /**
     * The closing of an XML element
     */
    data class EndElement(val qualifiedName: QualifiedName) : XmlToken() {
        // Convenience constructor for name-only nodes.
        constructor(name: String) : this(QualifiedName(name))
    }

    /**
     * An XML element text as string
     */
    data class Text(val value: String?) : XmlToken()

    object StartDocument : XmlToken()

    /**
     * The end of the XML stream to signal that the XML-encoded value has no more
     * tokens
     */
    object EndDocument : XmlToken()

    override fun toString(): String = when (this) {
        is BeginElement -> "<${this.qualifiedName}>"
        is EndElement -> "</${this.qualifiedName}>"
        is Text -> "${this.value}"
        StartDocument -> "[StartDocument]"
        EndDocument -> "[EndDocument]"
    }
}
