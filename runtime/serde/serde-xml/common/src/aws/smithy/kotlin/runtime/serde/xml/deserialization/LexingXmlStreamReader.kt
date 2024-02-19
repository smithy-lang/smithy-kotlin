/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.xml.deserialization

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.serde.DeserializationException
import aws.smithy.kotlin.runtime.serde.xml.XmlStreamReader
import aws.smithy.kotlin.runtime.serde.xml.XmlToken
import aws.smithy.kotlin.runtime.serde.xml.terminates

/**
 * An [XmlStreamReader] that provides [XmlToken] elements from an [XmlLexer]. This class internally maintains a peek
 * state, [lastToken], etc., but delegates all parsing operations to the scanner.
 * @param source The [XmlLexer] to use for XML parsing.
 */
@InternalApi
public class LexingXmlStreamReader(private val source: XmlLexer) : XmlStreamReader {
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
        while (index > peekQueue.size && !source.endOfDocument) {
            peekQueue.addLast(source.parseNext()!!)
        }
        return peekQueue.getOrNull(index - 1)
    }

    override fun skipNext() {
        val peekToken = peek(1) ?: return
        val startDepth = peekToken.depth
        scanUntilDepth(startDepth, nextToken())
    }

    private tailrec fun scanUntilDepth(startDepth: Int, from: XmlToken?) {
        when {
            from == null || from is XmlToken.EndDocument -> return // End of document
            from is XmlToken.EndElement && from.depth == startDepth -> return // Returned to original start depth
            else -> scanUntilDepth(startDepth, nextToken()) // Keep scannin'!
        }
    }
    override fun skipCurrent() {
        scanUntilDepth(lastToken?.depth ?: 0, lastToken)
    }

    override fun subTreeReader(subtreeStartDepth: XmlStreamReader.SubtreeStartDepth): XmlStreamReader =
        if (peek(1).terminates(lastToken)) {
            // Special case—return an empty subtree _and_ advance the token.
            nextToken()
            EmptyXmlStreamReader(this)
        } else {
            ChildXmlStreamReader(this, subtreeStartDepth)
        }
}

/**
 * A child (i.e., subtree) XML stream reader that terminates after returning to the depth at which it started.
 * @param parent The [LexingXmlStreamReader] upon which this child reader is based.
 * @param subtreeStartDepth The depth termination method.
 */
private class ChildXmlStreamReader(
    private val parent: LexingXmlStreamReader,
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

    override fun skipCurrent() = parent.skipCurrent()

    override fun subTreeReader(subtreeStartDepth: XmlStreamReader.SubtreeStartDepth): XmlStreamReader =
        parent.subTreeReader(subtreeStartDepth)
}

/**
 * An empty XML stream reader that trivially returns `null` for all [nextToken] and [peek] invocations.
 * @param parent The [LexingXmlStreamReader] on which this child reader is based.
 */
private class EmptyXmlStreamReader(private val parent: XmlStreamReader?) : XmlStreamReader {
    override val lastToken: XmlToken?
        get() = parent?.lastToken

    override fun nextToken(): XmlToken? = null

    override fun peek(index: Int): XmlToken? = null

    override fun skipNext() = Unit
    override fun skipCurrent() = Unit
    override fun subTreeReader(subtreeStartDepth: XmlStreamReader.SubtreeStartDepth): XmlStreamReader = this
}

private fun <T> List<T>.getOrNull(index: Int): T? = if (index < size) this[index] else null

internal fun XmlStreamReader.emptyReader(parent: XmlStreamReader? = this): XmlStreamReader = EmptyXmlStreamReader(parent)
