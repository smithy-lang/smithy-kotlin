/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*

/**
 * A zero dependency Kotlin common compatible XML serializer that handles suspend-enabled streaming
 */
internal class DefaultXmlStreamReader(private val data: CharStream) : XmlStreamReader {
    private var peekedToken: XmlToken? = null
    private val stack = ElementStack()

    override suspend fun nextToken(): XmlToken = peekedToken?.also { peekedToken = null } ?: doNext()

    override suspend fun peek(): XmlToken = peekedToken ?: doNext().also { peekedToken = it }

    override fun currentDepth(): Int = stack.depth

    private tailrec suspend fun doNext(): XmlToken {

        val ch: Char? = data.peek()

        val token = try {
            when {
                ch == null -> handleEndDocument()
                ch == '<' -> parseElement()
                ch == '/' -> parseSelfClosedElement()
                !ch.isWhitespace() -> parseText()
                else -> {
                    data.burnWhile { it.isWhitespace() }
                    null
                }
            }
        } catch (e: Exception) {
            when (e) {
                is XmlGenerationException -> throw e
                else -> throw XmlGenerationException(e)
            }
        }
        return token ?: doNext()
    }

    /**
     * Handling for the final token in a document
     */
    private fun handleEndDocument(): XmlToken.EndDocument {
        stack.reset()
        return XmlToken.EndDocument
    }

    /**
     * Parse an xml element tag e.g. <hello>, including comment and preamble elements
     *
     * Delegates to [parseCloseElement] if this is a closing tag e.g. </hello>
     */
    private suspend fun parseElement(): XmlToken? {
        data.burn('<')
        val ch = data.nextOrThrow()
        when (ch) {
            '/' -> return parseCloseElement()
            '?' -> {
                burnPreamble()
                return null
            }
            '!' -> {
                burnComment()
                return null
            }
        }

        val name = parseName(ch) { it == '/' || it == '>' || it.isWhitespace() }

        data.burnWhile { it.isWhitespace() }

        val (attributes, newNamespaces) = parseAttributes()
        data.burn('>', optional = true)
        val parentNamespaces = stack.peek()?.namespaces ?: emptyMap()
        val inScopeNamespaces = parentNamespaces + newNamespaces
        val qualifiedName = name.toQualified(inScopeNamespaces)
        stack.push(Element(qualifiedName, inScopeNamespaces))
        return XmlToken.BeginElement(qualifiedName, attributes.mapKeys { it.key.toQualified(inScopeNamespaces) })
    }

    /**
     * Handle the [XmlToken.EndElement] token for a "self closing" tag e.g. <hello/>
     */
    private suspend fun parseSelfClosedElement(): XmlToken {
        data.burn("/>")
        return XmlToken.EndElement(stack.pop().name)
    }

    /**
     * Handle text blocks, including "un-escaping"
     */
    private suspend fun parseText(): XmlToken {
        val text = data.readEscapedUntil { it == '<' }
        return XmlToken.Text(text)
    }

    /**
     * Handle the closing element of an xml tag e.g. </hello>
     */
    private suspend fun parseCloseElement(): XmlToken {
        data.burnWhile { it.isWhitespace() }
        val expected = stack.pop()
        val name = parseName(data.nextOrThrow()) { it == '>' || it.isWhitespace() }.toQualified(expected.namespaces)
        if (name != expected.name) {
            throw XmlGenerationException("Unexpected close $name")
        }
        data.burn('>')
        return XmlToken.EndElement(name)
    }

    private tailrec suspend fun burnComment() {
        data.burnWhile(inclusive = true) { it != '-' }
        if (data.nextOrThrow() == '-' && data.nextOrThrow() == '>') {
            return
        }
        burnComment()
    }

    private suspend fun burnPreamble() {
        data.burnWhile(inclusive = true) { it != '>' }
    }

    private suspend fun parseName(first: Char, exitPredicate: (Char) -> Boolean): PrefixedName {
        if (first.isWhitespace()) throw XmlGenerationException("Invalid whitespace")

        val prefix = data.readUntil(first) {
            it == ':' || exitPredicate(it)
        }
        if (data.peekOrThrow() != ':') {
            return PrefixedName(prefix, null)
        }
        data.burn(':')
        val name = data.readUntil(exitPredicate = exitPredicate)
        return PrefixedName(name, prefix)
    }

    private suspend fun parseAttributes(): Pair<Map<PrefixedName, String>, Map<String, String>> {
        val attrs = mutableMapOf<PrefixedName, String>()
        val namespaces = mutableMapOf<String, String>()
        var ch = data.peekOrThrow()
        while (ch != '/' && ch != '>') {
            val name = parseName(data.nextOrThrow()) { it == '=' || it == '/' || it == '>' || it.isWhitespace() }
            data.burnWhile { it.isWhitespace() }
            if (data.nextOrThrow() != '=') {
                throw XmlGenerationException("Expected attribute value for $name")
            }
            data.burnWhile { it.isWhitespace() }
            val quote = data.nextOrThrow()
            val value = data.readEscapedUntil { it == quote }
            data.burn(quote)
            when {
                name.namespacePrefix == "xmlns" -> namespaces[name.name] = value
                name.name == "xmlns" && name.namespacePrefix == null -> namespaces[""] = value // default namespace
                else -> attrs[name] = value
            }
            data.burnWhile { it.isWhitespace() }
            ch = data.peekOrThrow()
        }
        return attrs to namespaces
    }

    data class PrefixedName(val name: String, val namespacePrefix: String?)

    private fun PrefixedName.toQualified(namespaces: Map<String, String>): XmlToken.QualifiedName {
        val namespace = namespacePrefix?.let {
            namespaces[it] ?: throw XmlGenerationException("Unknown namespace prefix $it")
        } ?: namespaces[""] // Default namespace
        return XmlToken.QualifiedName(name, namespace)
    }

    private suspend fun CharStream.readEscapedUntil(exitPredicate: (Char) -> Boolean): String {
        val buffer = StringBuilder()
        while (true) {
            if (exitPredicate(peekOrThrow())) {
                return buffer.toString()
            }
            when (val ch = nextOrThrow()) {
                '&' -> buffer.append(parseEscaped())
                else -> buffer.append(ch)
            }
        }
    }

    private suspend fun CharStream.parseEscaped(): Char {
        val escapeSequence = readUntil('&', inclusive = true) { it == ';' }
        return XML_ESCAPED_CHARS.entries.find { it.value == escapeSequence }?.key
            ?: throw XmlGenerationException("Unknown escape sequence $escapeSequence")
    }

    private data class Element(val name: XmlToken.QualifiedName, val namespaces: Map<String, String>)

    private class ElementStack {
        private val stack = mutableListOf<Element>()
        var depth = 0
            private set

        fun push(value: Element) {
            stack.add(value)
            depth = stack.size
        }

        fun peek(): Element? = stack.lastOrNull()

        fun pop(): Element {
            depth = stack.size
            return stack.removeLast()
        }

        fun reset() {
            stack.clear()
            depth = 0
        }
    }
}
