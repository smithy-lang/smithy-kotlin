/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.serde.xml

import org.xmlpull.mxp1.MXParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
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

    // NOTE: Because of the way peeking is managed, any code in this class wanting to get the next token must call
    // this method rather than calling `parser.nextToken()` directly.
    override fun takeNextToken(): XmlToken = pullToken(false)


    private fun pullToken(isPeek: Boolean): XmlToken {
        if (peekedToken != null) {
            val rv = peekedToken!!
            peekedToken = null
            if (!isPeek) {
                _currentToken = rv.token
                println("Pulled token $_currentToken")
            }
            return rv.token
        }

        try {
            val rv = when (val nt = parser.nextToken()) {
                XmlPullParser.START_DOCUMENT -> pullToken(isPeek)
                XmlPullParser.END_DOCUMENT -> XmlToken.EndDocument
                XmlPullParser.START_TAG -> XmlToken.BeginElement(parser.qualifiedName(), parseAttributes())
                XmlPullParser.END_TAG -> XmlToken.EndElement(parser.qualifiedName())
                XmlPullParser.CDSECT,
                XmlPullParser.COMMENT,
                XmlPullParser.DOCDECL,
                XmlPullParser.IGNORABLE_WHITESPACE -> pullToken(isPeek)
                XmlPullParser.TEXT -> {
                    if (parser.text.blankToNull() == null) pullToken(isPeek)
                    else XmlToken.Text(parser.text.blankToNull())
                }
                else -> throw IllegalStateException("Unhandled tag $nt")
            }

            if (!isPeek) {
                _currentToken = rv
                println("Pulled token $_currentToken")
            }
            return rv
        } catch (e: Exception) {
            throw XmlGenerationException(e)
        }
    }

    // Create qualified name from current node
    private fun XmlPullParser.qualifiedName(): XmlToken.QualifiedName =
        XmlToken.QualifiedName(name, namespace.blankToNull(), prefix.blankToNull())

    // Return attribute map from attributes of current node
    private fun parseAttributes(): Map<XmlToken.QualifiedName, String> {
        if (parser.attributeCount == 0) return emptyMap()

        return (0 until parser.attributeCount)
            .asSequence()
            .map { attributeIndex ->
                XmlToken.QualifiedName(
                    parser.getAttributeName(attributeIndex),
                    parser.getAttributeNamespace(attributeIndex).blankToNull()
                ) to parser.getAttributeValue(attributeIndex)
            }
            .toMap()
    }

    // This does one of three things:
    // 1: if the next token is BeginElement, then that node is skipped
    // 2: if the next token is Text or EndElement, read tokens until the end of the current node is exited
    // 3: if the next token is EndDocument, NOP
    override fun skipNext() {
        val startDepth = parser.depth

        when (peekNextToken()) {
            is XmlToken.EndDocument -> return
            else -> traverseNode(takeNextToken(), startDepth)
        }

        require(startDepth == parser.depth) { "Expected to maintain parser depth after skip, but started at $startDepth and now at ${parser.depth}" }
    }

    override val currentToken: XmlToken
        get() = _currentToken

    tailrec fun traverseNode(st: XmlToken, startDepth: Int) {
        if (st == XmlToken.EndDocument) return
        if (st is XmlToken.EndElement && parser.depth == startDepth) return
        val next = takeNextToken()
        require(parser.depth >= startDepth) { "Traversal depth ${parser.depth} exceeded start node depth $startDepth" }
        return traverseNode(next, startDepth)
    }

    override fun peekNextToken(): XmlToken = when (peekedToken) {
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
