/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.CharStream

/**
 * Raw tokens produced when reading a XML document as a stream
 */
sealed class XmlToken {
    /**
     * Defines the name and namespace of an element
     */
    data class QualifiedName(val name: String, val namespace: String? = null)

    /**
     * The opening of an XML element
     */
    data class BeginElement(
        val id: QualifiedName,
        val attributes: Map<QualifiedName, String> = emptyMap()
    ) : XmlToken() {
        // Convenience constructor for name-only nodes.
        constructor(name: String) : this(QualifiedName(name))

        // Convenience constructor for name-only nodes with attributes.
        constructor(name: String, attributes: Map<QualifiedName, String>) : this(QualifiedName(name), attributes)
    }

    /**
     * The closing of an XML element
     */
    data class EndElement(val name: QualifiedName) : XmlToken() {
        // Convenience constructor for name-only nodes.
        constructor(name: String) : this(QualifiedName(name))
    }

    /**
     * An XML element text as string
     */
    data class Text(val value: String?) : XmlToken()

    /**
     * The end of the XML stream to signal that the XML-encoded value has no more
     * tokens
     */
    object EndDocument : XmlToken()

    override fun toString(): String = when (this) {
        is BeginElement -> "<${this.id}>"
        is EndElement -> "</${this.name}>"
        is Text -> "${this.value}"
        EndDocument -> "[EndDocument]"
    }
}

interface XmlStreamReader {

    /**
     *
     * @throws XmlGenerationException upon any error.
     */
    suspend fun nextToken(): XmlToken

    /**
     * Peek at the next token type.  Successive calls will return the same value, meaning there is only one
     * look-ahead at any given time during the parsing of input data.
     */
    suspend fun peek(): XmlToken

    /**
     * Return the current node depth of the parser.
     */
    fun currentDepth(): Int
}

/**
 * Recursively skip the next token. Meant for discarding unwanted/unrecognized nodes in an XML document
 *
 * This does one of three things:
 * 1: if the next token is BeginElement, then that node is skipped
 * 2: if the next token is Text or EndElement, read tokens until the end of the current node is exited
 * 3: if the next token is EndDocument, NOP
 */
suspend fun XmlStreamReader.skipNext() {
    val startDepth = currentDepth()

    while (true) {
        val next = peek()
        when {
            next is XmlToken.EndDocument -> break
            next is XmlToken.EndElement && currentDepth() == startDepth -> {
                nextToken() // This is the node we want so move to it
                break
            }
        }
        nextToken()
    }
    require(startDepth == currentDepth()) { "Expected to maintain parser depth after skip, but started at $startDepth and now at ${currentDepth()}" }
}

/*
* Creates an [XmlStreamReader] instance
*/
internal fun xmlStreamReader(payload: ByteArray): XmlStreamReader = DefaultXmlStreamReader(CharStream.fromByteArray(payload))
