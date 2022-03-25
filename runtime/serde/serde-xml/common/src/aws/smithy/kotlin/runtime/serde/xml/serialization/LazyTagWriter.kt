/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde.xml.serialization

import aws.smithy.kotlin.runtime.serde.xml.XmlToken.QualifiedName

private const val INDENT_PER_LEVEL = "    "

/**
 * An object that accumulates information for a single XML tag (e.g., attributes, child nodes, etc.) and lazily writes
 * upon calling [write].
 * @param pretty A flag which indicates whether written output should be "pretty" or not. See definition of "pretty" at
 * [BufferingXmlStreamWriter].
 * @param indentLevel The indentation level of the current writer. This only matters if [pretty] is `true`.
 * @param qName The [QualifiedName] of this tag.
 * @param nsAttributes The namespace declaration attributes for this tag (as a [Map] of [QualifiedName] keys to [String]
 * values).
 */
class LazyTagWriter(
    val pretty: Boolean,
    val indentLevel: Int,
    val qName: QualifiedName,
    val nsAttributes: Map<QualifiedName, String>,
) {
    private val attributes = mutableMapOf<QualifiedName, String?>()
    private val children = mutableListOf<TagChild>()
    private val tagIndentationSpace = INDENT_PER_LEVEL.repeat(indentLevel)
    private val textIndentationSpace = INDENT_PER_LEVEL.repeat(indentLevel + 1)

    init {
        attributes.putAll(nsAttributes)
    }

    /**
     * Adds an attribute to this tag.
     * @param qName The [QualifiedName] of the attribute.
     * @param value The value of the attribute.
     */
    fun attribute(qName: QualifiedName, value: String?) {
        attributes[qName] = value
    }

    /**
     * Adds a child tag to this (parent) tag.
     * @param childWriter The already-initialized [LazyTagWriter] for the child.
     */
    fun childTag(childWriter: LazyTagWriter) {
        children.add(TagChild.Tag(childWriter))
    }

    /**
     * Adds a child text node to this tag.
     * @param text The contents of the text node.
     */
    fun text(text: String) {
        children.add(TagChild.Text(text))
    }

    /**
     * Writes the accumulated information about this tag to the given [buffer]. Note that this function recursively
     * calls [write] on all child tag writers. Thus, it only needs to be called explicitly for the root tag writer.
     * @param buffer The [StringBuilder] in which to write the serialized tag.
     */
    fun write(buffer: StringBuilder) {
        buffer
            .appendIfPretty(tagIndentationSpace)
            .append('<')
            .append(qName)

        attributes.forEach { e ->
            buffer
                .append(' ')
                .append(e.key)
                .append("=\"")

            val value = e.value
            if (value != null) buffer.appendXmlEscapedForAttribute(value)

            buffer.append('"')
        }

        when {
            children.isEmpty() -> {
                // Self closing tag (e.g., `<foo />`)
                buffer
                    .appendIfPretty(" ")
                    .append("/>")
                    .appendLineIfPretty()
            }

            children.size == 1 && children.first() is TagChild.Text -> {
                // Text inline tag (e.g., `<foo>bar</foo>`)
                buffer
                    .append('>')
                    .appendXmlEscaped((children.first() as TagChild.Text).text)
                    .append("</")
                    .append(qName)
                    .append('>')
                    .appendLineIfPretty()
            }

            else -> {
                // Nesting tag (e.g., `
                //   <foo>
                //     ...
                //   </foo>
                // `)
                buffer
                    .append('>')
                    .appendLineIfPretty()

                children.forEach { child ->
                    when (child) {
                        is TagChild.Text -> {
                            buffer
                                .appendIfPretty(textIndentationSpace)
                                .appendXmlEscaped(child.text)
                                .appendLineIfPretty()
                        }

                        is TagChild.Tag -> child.lazyTagWriter.write(buffer)
                    }
                }

                buffer
                    .appendIfPretty(tagIndentationSpace)
                    .append("</")
                    .append(qName)
                    .append('>')
                    .appendLineIfPretty()
            }
        }
    }

    private fun StringBuilder.appendIfPretty(text: String): StringBuilder {
        if (pretty) append(text)
        return this
    }

    private fun StringBuilder.appendLineIfPretty(): StringBuilder {
        if (pretty) appendLine()
        return this
    }

    private fun StringBuilder.appendXmlEscaped(text: String): StringBuilder {
        text.forEach { char ->
            when (char) {
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '&' -> append("&amp;")
                in '\u0000'..'\u001F' -> append("&#x${char.hexCode()};")
                '\u0085' -> append("&#x85;")
                '\u2028' -> append("&#x2028;")
                else -> append(char)
            }
        }
        return this
    }

    private fun StringBuilder.appendXmlEscapedForAttribute(text: String): StringBuilder {
        text.forEach { char ->
            when (char) {
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '&' -> append("&amp;")
                '"' -> append("&quot;")
                in '\u0000'..'\u001F' -> append("&#x${char.hexCode()};")
                '\u0085' -> append("&#x85;")
                '\u2028' -> append("&#x2028;")
                else -> append(char)
            }
        }
        return this
    }
}

/**
 * Encodes a character code to hex (e.g., '\n' → "A")
 */
private fun Char.hexCode(): String = code.toString(radix = 16).uppercase()
