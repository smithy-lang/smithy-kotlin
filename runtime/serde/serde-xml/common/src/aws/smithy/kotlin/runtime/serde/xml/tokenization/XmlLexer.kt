/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde.xml.tokenization

import aws.smithy.kotlin.runtime.serde.DeserializationException
import aws.smithy.kotlin.runtime.serde.xml.XmlStreamReader
import aws.smithy.kotlin.runtime.serde.xml.XmlToken
import aws.smithy.kotlin.runtime.serde.xml.terminates

/**
 * An [XmlStreamReader] that provides [XmlToken] elements from an [XmlScanner]. This class internally maintains a peek
 * state, [lastToken], etc., but delegates all parsing operations to the scanner.
 * @param source The [XmlScanner] to use for XML parsing.
 */
class XmlLexer(private val source: XmlScanner) : XmlStreamReader {
    private val peekQueue = ArrayDeque<XmlToken>()

    /**
     * Throws a [DeserializationException] with the given message and location string.
     * @param msg The error message to include with the exception.
     */
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun error(msg: String): Nothing = source.error(msg)

    override var lastToken: XmlToken? = null
        private set

    override fun nextToken(): XmlToken? =
        (peekQueue.removeFirstOrNull() ?: source.parseNext()).also { lastToken = it }

    override fun peek(index: Int): XmlToken? {
        while (index > peekQueue.size && source.state != ScannerState.EndOfDocument) {
            peekQueue.addLast(source.parseNext()!!)
        }
        return peekQueue.getOrNull(index - 1)
    }

    override fun skipNext() {
        val peekToken = peek(1) ?: return
        val startDepth = peekToken.depth

        tailrec fun scanUntilDepth(from: XmlToken?) {
            when {
                // TODO Is EndDocument actually returned in the XmlStreamReaderXmlPull implementation? If not, remove...
                from == null || from is XmlToken.EndDocument -> return // End of document
                from is XmlToken.EndElement && from.depth == startDepth -> return // Returned to original start depth
                else -> scanUntilDepth(nextToken()) // Keep scannin'!
            }
        }

        scanUntilDepth(nextToken())
    }

    override fun subTreeReader(subtreeStartDepth: XmlStreamReader.SubtreeStartDepth): XmlStreamReader =
        if (peek(1).terminates(lastToken)) {
            // Special case—return an empty subtree _and_ advance the token.
            nextToken()
            EmptyChildXmlLexer(this)
        } else {
            ChildXmlLexer(this, subtreeStartDepth)
        }
}

/**
 * A child (i.e., subtree) lexer that terminates after returning to the depth at which it started.
 * @param parent The [XmlLexer] upon which this child lexer is based.
 * @param subtreeStartDepth The depth termination method
 */
private class ChildXmlLexer(
    private val parent: XmlLexer,
    private val subtreeStartDepth: XmlStreamReader.SubtreeStartDepth,
) : XmlStreamReader {
    override val lastToken: XmlToken?
        get() = parent.lastToken

    private val minimumDepth = when (subtreeStartDepth) {
        XmlStreamReader.SubtreeStartDepth.CHILD -> lastToken?.depth?.plus(1)
        XmlStreamReader.SubtreeStartDepth.CURRENT -> lastToken?.depth
    } ?: error("Unable to determine depth of last node")

    /**
     * Throws a [DeserializationException] with the given message and location string.
     * @param msg The error message to include with the exception.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun error(msg: String): Nothing = parent.error(msg)

    override fun nextToken(): XmlToken? {
        val next = parent.peek(1) ?: return null

        val peekToken = when {
            subtreeStartDepth == XmlStreamReader.SubtreeStartDepth.CHILD && next.depth < minimumDepth -> {
                val subsequent = parent.peek(2) ?: return null
                if (subsequent.depth >= minimumDepth) parent.nextToken()
                subsequent
            }
            else -> next
        }

        return if (peekToken.depth >= minimumDepth) parent.nextToken() else null
    }

    override fun peek(index: Int): XmlToken? {
        val peekToken = parent.peek(index) ?: return null
        return if (peekToken.depth >= minimumDepth) peekToken else null
    }

    override fun skipNext() = parent.skipNext()

    override fun subTreeReader(subtreeStartDepth: XmlStreamReader.SubtreeStartDepth): XmlStreamReader =
        parent.subTreeReader(subtreeStartDepth)
}

/**
 * An empty child lexer that trivially returns `null` for all [nextToken] and [peek] invocations.
 * @param parent The [XmlLexer] on which this child lexer is based.
 */
private class EmptyChildXmlLexer(private val parent: XmlStreamReader) : XmlStreamReader {
    override val lastToken: XmlToken?
        get() = parent.lastToken

    override fun nextToken(): XmlToken? = null

    override fun peek(index: Int): XmlToken? = null

    override fun skipNext() = Unit

    override fun subTreeReader(subtreeStartDepth: XmlStreamReader.SubtreeStartDepth): XmlStreamReader = this
}

private fun <T> List<T>.getOrNull(index: Int): T? = if (index < size) this[index] else null
