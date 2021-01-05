/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

/**
 * A zero dependency Kotlin common compatible XML serializer
 */
class SimpleXmlStreamReader(payload: ByteArray) : XmlStreamReader {

    private val payload = payload.decodeToString()

    private var peekedToken: XmlToken? = null
    private var depth = 0
    private var position = 0
    private val stack = mutableListOf<XmlToken.QualifiedName>()

    init {
        // Advance to the start of the first node
        loop@ while (position < this.payload.length) {
            val start = this.payload.indexOfRequired('<', startIndex = position)

            this.payload.indexOfRequired('>', startIndex = start)
            position = start
            // Ignore pre-amble and comments
            when (this.payload[start + 1]) {
                '!', '?' -> position = this.payload.indexOfRequired('>', startIndex = position)
                else -> break@loop
            }
        }
    }

    override tailrec fun nextToken(): XmlToken {
        peekedToken?.let {
            peekedToken = null
            return it
        }
        if (position >= payload.length) {
            depth = 0
            return XmlToken.EndDocument
        }
        return when (payload[position]) {
            '/' -> handleSelfClosedElement()
            '<' -> handleOpenElementTag()
            else ->
                if (isTextNode()) {
                    textNode()
                } else {
                    // move to next open tag and recurse
                    position = payload.indexOfRequired('<', startIndex = position)
                    nextToken()
                }
        }
    }

    private fun isTextNode(): Boolean {
        val nextOpen = payload.indexOfRequired('<', startIndex = position)
        return payload.substring(position, nextOpen).isNotBlank()
    }

    private fun handleOpenElementTag(): XmlToken = when (payload[position + 1]) {
        '/' -> endElement()
        '!' -> handleComment()
        else -> beginElement()
    }

    private fun handleComment(): XmlToken {
        val endOfComment = payload.indexOf("-->", startIndex = position)
        position = endOfComment + 3
        return nextToken()
    }

    private fun handleSelfClosedElement(): XmlToken.EndElement {
        depth = stack.size
        position += 2 // Move past the '/>'
        return XmlToken.EndElement(stack.removeLast())
    }

    override fun skipNext() {
        val curDepth = depth
        while (position < payload.length) {
            val next = nextToken()
            if (next is XmlToken.EndElement && depth <= curDepth) {
                return
            }
        }
    }

    override fun peek(): XmlToken = peekedToken ?: nextToken().also {
        peekedToken = it
    }

    override fun currentDepth(): Int = depth

    private fun beginElement(): XmlToken.BeginElement {
        val end = payload.indexOfRequired('>', startIndex = position)
        val isSelfClosing = payload[end - 1] == '/'
        val contentsEnd = if (isSelfClosing) end - 1 else end
        val elementContents = payload.substring(position + 1, contentsEnd).split(' ')
        val name = XmlToken.QualifiedName(elementContents.first())

        stack.add(name)
        depth = stack.size

        val attributes = handleAttributes(elementContents)
        position = if (isSelfClosing) contentsEnd else end + 1
        return XmlToken.BeginElement(name, attributes)
    }

    private fun handleAttributes(elementContents: List<String>) = elementContents
        .drop(1) // don't need the
        .filter { it.isNotBlank() }
        .map {
            val components = it.split('=')
            val attrName = components.first()
            XmlToken.QualifiedName(attrName) to components[1].trim('"')
        }.toMap()

    private fun textNode(): XmlToken.Text {
        val end = payload.indexOfRequired('<', startIndex = position)
        val contents = payload.substring(position, end)
        position = end
        return XmlToken.Text(contents)
    }

    private fun endElement(): XmlToken.EndElement {
        val end = payload.indexOfRequired('>', startIndex = position)
        val name = XmlToken.QualifiedName(payload.substring(position + 2, end))
        depth = stack.size
        val expected = stack.removeLast()
        if (name != expected) {
            throw XmlGenerationException("XML document is invalid (expected: $expected, got: $name)")
        }
        position = end + 1
        return XmlToken.EndElement(name)
    }

    private fun String.indexOfRequired(ch: Char, startIndex: Int): Int = indexOf(ch, startIndex = startIndex).also {
        if (it < 0) {
            throw XmlGenerationException("XML document is invalid (expected character '$ch' is not present after position $startIndex)")
        }
    }
}
