/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.serde.xml

import aws.smithy.kotlin.runtime.serde.xml.deserialization.LexingXmlStreamReader
import aws.smithy.kotlin.runtime.serde.xml.deserialization.StringTextStream
import aws.smithy.kotlin.runtime.serde.xml.deserialization.XmlLexer

/**
 * Provides stream-style access to an XML payload.  This abstraction
 * supports the ability to look ahead an arbitrary number of elements.  It can also
 * create "views" to subtrees of the document, guaranteeing that clients do not exceed bounds.
 */
interface XmlStreamReader {
    /**
     * Specify the depth for which a subtree is created.
     */
    enum class SubtreeStartDepth {
        /**
         * The subtree's minimum depth is the same as the current node depth.
         */
        CURRENT,
        /**
         * The subtree's minimum depth is the same as the current node depth + 1.
         */
        CHILD,
    }
    /**
     * Return the last token that was consumed by the reader.
     */
    val lastToken: XmlToken?

    /**
     * Return another reader that starts and terminates at the current level (CURRENT) or the
     * current level + 1 (CHILD), starting at the next node to be read from the stream.
     * @param subtreeStartDepth Determines minimum depth of the subtree
     */
    fun subTreeReader(subtreeStartDepth: SubtreeStartDepth = SubtreeStartDepth.CHILD): XmlStreamReader

    /**
     * Return the next actionable token or null if stream is exhausted.
     *
     * @throws [aws.smithy.kotlin.runtime.serde.DeserializationException] upon any error.
     */
    fun nextToken(): XmlToken?

    /**
     * Recursively skip the next token. Meant for discarding unwanted/unrecognized nodes in an XML document
     */
    fun skipNext()

    /**
     * Peek at the next token type.  Successive calls will return the same value, meaning there is only one
     * look-ahead at any given time during the parsing of input data.
     * @param index a positive integer representing index of node from current to peek.  Index of 1 is the next node.
     */
    fun peek(index: Int = 1): XmlToken?
}

/**
 * Seek from the current token onward to find a token of specified type and predication.
 *
 * @param selectionPredicate predicate that evaluates nodes of the required type to match
 */
inline fun <reified T : XmlToken> XmlStreamReader.seek(selectionPredicate: (T) -> Boolean = { true }): T? {
    var token: XmlToken? = lastToken

    do {
        val foundMatch = if (token is T) selectionPredicate.invoke(token) else false
        if (!foundMatch) token = nextToken()
    } while (token != null && !foundMatch)

    return token as T?
}

/**
 * Creates an [XmlStreamReader] instance
 */
fun xmlStreamReader(payload: ByteArray): XmlStreamReader {
    val stream = StringTextStream(payload.decodeToString())
    val lexer = XmlLexer(stream)
    return LexingXmlStreamReader(lexer)
}
