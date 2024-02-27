/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.xml

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.serde.DeserializationException

/**
 * An [XmlStreamReader] scoped to reading a single XML element [tag]
 * XmlTagReader provides a "tag" scoped view into an XML document. Methods return
 * `null` when the current tag has been exhausted.
 */
@InternalApi
public class XmlTagReader(
    public val tag: XmlToken.BeginElement,
    private val reader: XmlStreamReader,
) {
    // last tag we emitted and returned to the caller
    private var lastEmitted: XmlTagReader? = null
    private var closed = false

    /**
     * Get the fully qualified tag name of [tag]
     */
    public val tagName: String
        get() = tag.name.toString()

    /**
     * Return the next actionable token or null if stream is exhausted.
     */
    public fun nextToken(): XmlToken? {
        if (closed) return null
        val peek = reader.peek()
        if (peek.terminates(tag)) {
            // consume it and close the tag reader
            reader.nextToken()
            closed = true
            return null
        }
        return reader.nextToken()
    }

    /**
     * Check if the next token has a value, returns false if [XmlToken.EndElement]
     * would be returned.
     */
    public fun nextHasValue(): Boolean {
        if (closed) return false
        return reader.peek() !is XmlToken.EndElement
    }

    /**
     * Exhaust this [XmlTagReader] to completion. This should always
     * be invoked to maintain deserialization state.
     */
    public fun drop() {
        do {
            val tok = nextToken()
        } while (tok != null)
    }

    /**
     * Return an [XmlTagReader] for the next [XmlToken.BeginElement]. The returned reader
     * is only valid until [nextTag] is called or [drop] is invoked on it, whichever comes first.
     */
    public fun nextTag(): XmlTagReader? {
        lastEmitted?.drop()

        var cand = nextToken()
        while (cand != null && cand !is XmlToken.BeginElement) {
            cand = nextToken()
        }

        val nextTok = cand as? XmlToken.BeginElement

        return nextTok?.tagReader(reader).also { newScope ->
            lastEmitted = newScope
        }
    }
}

/**
 * Get a [XmlTagReader] for the root tag. This is the entry point for beginning
 * deserialization.
 */
@InternalApi
public fun xmlTagReader(payload: ByteArray): XmlTagReader =
    xmlStreamReader(payload).root()

private fun XmlStreamReader.root(): XmlTagReader {
    val start = seek<XmlToken.BeginElement>() ?: error("expected start tag: last = $lastToken")
    return start.tagReader(this)
}

/**
 * Create a new reader scoped to this element.
 */
@InternalApi
public fun XmlToken.BeginElement.tagReader(reader: XmlStreamReader): XmlTagReader {
    val start = reader.lastToken as? XmlToken.BeginElement ?: error("expected start tag found ${reader.lastToken}")
    check(name == start.name) { "expected start tag $name but current reader state is on ${start.name}" }
    return XmlTagReader(this, reader)
}

/**
 * Unwrap the next token as [XmlToken.Text] and return its value or throw a [DeserializationException]
 */
@InternalApi
public fun XmlTagReader.data(): String =
    when (val next = nextToken()) {
        is XmlToken.Text -> next.value ?: ""
        null, is XmlToken.EndElement -> ""
        else -> throw DeserializationException("expected XmlToken.Text element, found $next")
    }

/**
 * Attempt to get the text token as [XmlToken.Text] and return a result containing its' value on success
 * or the exception thrown on failure.
 */
@InternalApi
public fun XmlTagReader.tryData(): Result<String> = runCatching { data() }
