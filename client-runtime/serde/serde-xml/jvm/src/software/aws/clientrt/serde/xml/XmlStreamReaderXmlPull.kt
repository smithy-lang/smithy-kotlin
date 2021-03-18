/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.serde.xml

import org.xmlpull.mxp1.MXParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import software.aws.clientrt.serde.xml.dom.push
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

private class XmlStreamReaderXmlPull(
    payload: ByteArray,
    charset: Charset = Charsets.UTF_8,
    private val parser: XmlPullParser = xmlPullParserFactory()
) : XmlStreamReader {

    data class PeekState(val token: XmlToken, val depth: Int)

    private var _currentToken: XmlToken = XmlToken.StartDocument
    private var peekedToken: PeekState? = null

    // In the case that a text node contains escaped characters,
    // the parser returns each of these as independent tokens however
    // they only represent a subset of the text value of a node.  To
    // deal with this we introduce a sub-token parsing mode in which
    // tokens are read and concatenated such that the client is only aware of
    // the complete Text values.
    private val subTokenStack = mutableListOf<String>()

    // In some cases a low-level token parse event may result in two high-level
    // tokens.  The following property is used to hold that second
    // token to be returned upon a subsequent call to nextToken().
    private var futureToken: XmlToken? = null

    init {
        parser.setFeature(MXParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(ByteArrayInputStream(payload), charset.toString())
    }

    companion object {
        private fun xmlPullParserFactory(): XmlPullParser {
            val factory = XmlPullParserFactory.newInstance("org.xmlpull.mxp1.MXParser", null)
            return factory.newPullParser()
        }
    }

    override fun toString(): String {
        return _currentToken.toString()
    }

    override suspend fun nextToken(): XmlToken = pullToken(false)

    // Use the existence of sub tokens to determine if in general
    // parsing mode or in sub token parsing mode.
    private val isGeneralParseMode get() = subTokenStack.isEmpty()

    /**
     * @param isPeek if true, the value returned will still be taken upon the next call to nextToken().
     */
    private fun pullToken(isPeek: Boolean): XmlToken {
        if (peekedToken != null) {
            val rv = peekedToken!!
            peekedToken = null
            if (!isPeek) {
                _currentToken = rv.token
            }
            return rv.token
        }

        // If a previous call to pullToken generated more than one token, the latter is
        // set to `futureToken` to be returned upon next call.
        if (futureToken != null) {
            val t = futureToken
            futureToken = null
            return t!!
        }

        try {
            val rv = when (val nt = parser.nextToken()) {
                XmlPullParser.START_DOCUMENT -> pullToken(isPeek)
                XmlPullParser.END_DOCUMENT -> XmlToken.EndDocument
                XmlPullParser.START_TAG -> XmlToken.BeginElement(parser.qualifiedName(), parseAttributes(), parser.currDeclaredNamespaces())
                XmlPullParser.END_TAG -> when {
                    isGeneralParseMode -> XmlToken.EndElement(parser.qualifiedName())
                    else -> {
                        check(futureToken == null) { "Expected futureToken to be null." }
                        futureToken = XmlToken.EndElement(parser.qualifiedName())
                        val nodeContent = subTokenStack.joinToString(separator = "") { it }
                        subTokenStack.clear()
                        XmlToken.Text(nodeContent)
                    }
                }
                XmlPullParser.CDSECT,
                XmlPullParser.COMMENT,
                XmlPullParser.DOCDECL,
                XmlPullParser.IGNORABLE_WHITESPACE -> pullToken(isPeek)
                XmlPullParser.TEXT -> when {
                    parser.text.isNullOrBlank() -> pullToken(isPeek)
                    isGeneralParseMode -> XmlToken.Text(parser.text.blankToNull())
                    else -> {
                        subTokenStack.push(parser.text)
                        pullToken(isPeek)
                    }
                }
                XmlPullParser.ENTITY_REF -> when (parser.text) {
                    null -> pullToken(isPeek)
                    else -> {
                        subTokenStack.push(parser.text)
                        pullToken(isPeek)
                    }
                }
                else -> throw IllegalStateException("Unhandled tag $nt (${XmlPullParser.TYPES[nt]})")
            }

            if (!isPeek) {
                _currentToken = rv
            }
            return rv
        } catch (e: Exception) {
            throw XmlGenerationException(e)
        }
    }

    // Create qualified name from current node
    private fun XmlPullParser.qualifiedName(): XmlToken.QualifiedName =
        XmlToken.QualifiedName(name, namespace.blankToNull(), prefix.blankToNull())

    // get a list of all namespaces declared in this element
    private fun XmlPullParser.currDeclaredNamespaces(): List<XmlToken.Namespace> {
        val nsStart = getNamespaceCount(depth - 1)
        val nsEnd = getNamespaceCount(depth)
        if (nsStart >= nsEnd) return emptyList()
        val decls = mutableListOf<XmlToken.Namespace>()
        for (i in nsStart until nsEnd) {
            val prefix = getNamespacePrefix(i)
            val ns = getNamespaceUri(i)
            decls.add(XmlToken.Namespace(ns, prefix))
        }
        return decls
    }

    // Return attribute map from attributes of current node
    private fun parseAttributes(): Map<XmlToken.QualifiedName, String> {
        if (parser.attributeCount == 0) return emptyMap()

        return (0 until parser.attributeCount)
            .asSequence()
            .map { attributeIndex ->
                XmlToken.QualifiedName(
                    parser.getAttributeName(attributeIndex),
                    parser.getAttributeNamespace(attributeIndex).blankToNull(),
                    parser.getAttributePrefix(attributeIndex).blankToNull()
                ) to parser.getAttributeValue(attributeIndex)
            }
            .toMap()
    }

    // This does one of three things:
    // 1: if the next token is BeginElement, then that node is skipped
    // 2: if the next token is Text or EndElement, read tokens until the end of the current node is exited
    // 3: if the next token is EndDocument, NOP
    override suspend fun skipNext() {
        val startDepth = parser.depth

        when (peek()) {
            is XmlToken.EndDocument -> return
            else -> traverseNode(nextToken(), startDepth)
        }

        require(startDepth == parser.depth) { "Expected to maintain parser depth after skip, but started at $startDepth and now at ${parser.depth}" }
    }

    override val currentToken: XmlToken
        get() = _currentToken

    tailrec suspend fun traverseNode(st: XmlToken, startDepth: Int) {
        if (st == XmlToken.EndDocument) return
        if (st is XmlToken.EndElement && parser.depth == startDepth) return
        val next = nextToken()
        require(parser.depth >= startDepth) { "Traversal depth ${parser.depth} exceeded start node depth $startDepth" }
        return traverseNode(next, startDepth)
    }

    override suspend fun peek(): XmlToken = when (peekedToken) {
        null -> {
            val currentDepth = parser.depth
            peekedToken = PeekState(pullToken(true), currentDepth)
            peekedToken!!.token
        }
        else -> peekedToken!!.token
    }

    override val currentDepth: Int
        get() = if (peekedToken != null) peekedToken!!.depth else parser.depth
}

private fun String?.blankToNull(): String? = if (this?.isBlank() != false) null else this

/*
* Creates a [JsonStreamReader] instance
*/
internal actual fun xmlStreamReader(payload: ByteArray): XmlStreamReader = XmlStreamReaderXmlPull(payload)
