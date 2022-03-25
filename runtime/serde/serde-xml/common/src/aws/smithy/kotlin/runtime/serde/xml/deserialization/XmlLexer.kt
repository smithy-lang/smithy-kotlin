/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde.xml.deserialization

import aws.smithy.kotlin.runtime.serde.DeserializationException
import aws.smithy.kotlin.runtime.serde.xml.XmlToken
import aws.smithy.kotlin.runtime.util.text.codePointToChars

private val decimalCharRef = "#([0-9]+)".toRegex()
private val hexCharRef = "#x([0-9a-fA-F]+)".toRegex()

// https://www.w3.org/TR/xml/#sec-predefined-ent
private val namedRefs = mapOf(
    "lt" to '<',
    "gt" to '>',
    "amp" to '&',
    "apos" to '\'',
    "quot" to '"',
).mapValues { charArrayOf(it.value) }

private fun XmlToken.QualifiedName.isXmlns(): Boolean = (local == "xmlns" && prefix == null) || (prefix == "xmlns")
private fun XmlToken.QualifiedName.xmlnsPrefix(): String? = if (local == "xmlns") null else local

private typealias AttributeMap = Map<XmlToken.QualifiedName, String>
private fun AttributeMap.extractNsDeclarations(): Pair<AttributeMap, List<XmlToken.Namespace>> {
    val parts = toList().partition { it.first.isXmlns() }
    val attributes = parts.second.toMap()
    val nsDeclarations = parts.first.map { XmlToken.Namespace(it.second, it.first.xmlnsPrefix()) }
    return attributes to nsDeclarations
}

/**
 * A lexer that scans a [StringTextStream] and reads [XmlToken] elements.
 */
class XmlLexer(internal val source: StringTextStream) {
    private var state: LexerState = LexerState.Initial

    val endOfDocument: Boolean
        get() = state == LexerState.EndOfDocument

    /**
     * Throws a [DeserializationException] with the given message and location string.
     * @param msg The error message to include with the exception.
     */
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun error(msg: String): Nothing = source.error(msg)

    /**
     * Parses the next token from the source.
     * @return The next [XmlToken] in the source, or null if the end of the source has been reached.
     */
    fun parseNext(): XmlToken? = when (val currentState = this.state) {
        LexerState.EndOfDocument -> null

        is LexerState.Tag.EmptyTag -> {
            state = currentState.parent ?: LexerState.EndOfDocument
            // In this case we don't actually need to read more from the source because an empty tag is represented as
            // a BeginElement (which was previously returned) and an EndElement (which will be returned now).
            XmlToken.EndElement(currentState.depth, currentState.name)
        }

        is LexerState.Tag.OpenTag ->
            if (source.peekMatches("<") && !source.peekMatches("<![CDATA[")) {
                readTagToken()
            } else {
                readTextToken()
            }

        LexerState.Initial -> {
            skipPreprocessingInstructions()
            state = LexerState.BeforeRootTag
            parseNext()
        }

        LexerState.BeforeRootTag -> {
            skipSpace()
            readTagToken()
        }
    }

    /**
     * Reads an attribute key and value from the source.
     */
    private fun readAttribute(): Pair<XmlToken.QualifiedName, String> {
        val name = readName()
        skipSpace()

        val equals = source.readOrThrow("trying to read attribute equals")
        if (equals != '=') error("Unexpected '$equals' while trying to read attribute equals")
        skipSpace()

        val value = readQuoted()

        return name to value
    }

    /**
     * Reads a CDATA section from the source. This assumes that the source position is immediately after the `<![CDATA[`
     * token.
     */
    private fun readCdata(): String {
        val body = source.readUntil("]]>", "trying to read CDATA content")
        source.advance(3, "trying to read end of CATA") // Skip trailing `]]>`
        return body
    }

    /**
     * Reads a tag or attribute name from the source.
     */
    private fun readName(): XmlToken.QualifiedName = source.readWhileXmlName().qualify()

    /**
     * Reads a quoted string from the source. The quotes may be single (') or double (").
     */
    private fun readQuoted(): String {
        val quoteChar = source.readOrThrow("trying to read attribute value")
        if (quoteChar != '\'' && quoteChar != '"') {
            error("Unexpected '$quoteChar' while trying to read attribute value")
        }

        return buildString {
            while (true) {
                when (val c = source.readOrThrow("trying to read a string")) {
                    '&' -> append(readReference())
                    '<' -> error("Unexpected '<' while trying to read a string")
                    quoteChar -> break
                    else -> append(c)
                }
            }
        }
    }

    /**
     * Reads a character reference (e.g., `#x1a2b;`) or entity reference (e.g., `apos;`) from the source. This assumes
     * that the leading `&` has already been consumed.
     */
    private fun readReference(): CharArray {
        val ref = source.readUntil(";", "trying to read a char/entity reference")
        source.advance(1, "trying to read the end of a char/entity reference")

        val decimalMatch = decimalCharRef.matchEntire(ref)
        if (decimalMatch != null) {
            val code = decimalMatch.groupValues[1].toInt()
            return Char.codePointToChars(code)
        }

        val hexMatch = hexCharRef.matchEntire(ref)
        if (hexMatch != null) {
            val code = hexMatch.groupValues[1].toInt(radix = 16)
            return Char.codePointToChars(code)
        }

        return namedRefs.getOrElse(ref) { error("Unknown reference '$ref'") }
    }

    /**
     * Reads a tag token from the source.
     */
    private fun readTagToken(): XmlToken {
        val lt = source.readOrThrow("looking for the start of a tag")
        if (lt != '<') error("Unexpected character '$lt' while looking for the start of a tag")

        if (source.advanceIf("!--")) {
            skipComment()
            return parseNext()!!
        }

        val token = if (source.advanceIf("/")) {
            val openTagState = state as LexerState.Tag.OpenTag
            val expectedName = openTagState.name
            val actualName = readName()
            if (actualName != expectedName) {
                error("Unexpected '/$actualName' tag while looking for '/$expectedName' tag")
            }

            skipSpace()

            val ch = source.readOrThrow("looking for the end of a tag")
            if (ch != '>') error("Unexpected character '$ch' while looking for the end of a tag")

            state = openTagState.parent ?: LexerState.EndOfDocument
            XmlToken.EndElement(openTagState.depth, actualName)
        } else {
            val openTagState = (state as? LexerState.Tag.OpenTag)?.copy(seenChildren = true)

            val name = readName()
            skipSpace()

            val allAttributes = mutableMapOf<XmlToken.QualifiedName, String>()
            var selfClosingTag = false
            while (true) {
                when (source.readOrThrow("looking for the end of a tag")) {
                    '/' -> {
                        selfClosingTag = true
                        break
                    }

                    '>' -> break

                    else -> {
                        source.rewind(1, "looking for the beginning of an attribute")
                        allAttributes += readAttribute()
                    }
                }
                skipSpace()
            }

            val (attributes, nsDeclarations) = allAttributes.extractNsDeclarations()

            val nextState = if (selfClosingTag) {
                val gt = source.readOrThrow("looking for the end of a tag")
                if (gt != '>') error("Unexpected characters while looking for the end of a tag")
                LexerState.Tag.EmptyTag(name, openTagState)
            } else {
                LexerState.Tag.OpenTag(name, openTagState, false)
            }

            state = nextState
            XmlToken.BeginElement(nextState.depth, name, attributes, nsDeclarations)
        }

        return token
    }

    /**
     * Reads a text token from the source.
     */
    private fun readTextToken(): XmlToken {
        var isBlank = true

        val text = buildString {
            while (true) {
                when (val nextCh = source.readOrThrow("reading text node")) {
                    ' ', '\t', '\r', '\n' -> append(nextCh)

                    '<' -> when {
                        source.advanceIf("!--") -> skipComment()

                        source.advanceIf("![CDATA[") -> {
                            append(readCdata())
                            isBlank = false
                        }

                        else -> {
                            source.rewind(1, "looking for the beginning of a tag")
                            break
                        }
                    }

                    '&' -> {
                        isBlank = false
                        append(readReference())
                    }

                    else -> {
                        isBlank = false
                        append(nextCh)
                    }
                }
            }
        }

        val openTagState = state as LexerState.Tag.OpenTag
        val openTagIsMostRecent = openTagState.seenChildren
        val closeTagIsNext = source.peekMatches("</")

        state = openTagState.copy(seenChildren = true)

        // Return a blank text node only if it's the only node in this tag; otherwise, skip to the next token
        if (isBlank && (openTagIsMostRecent || !closeTagIsNext)) {
            return parseNext()!!
        }

        return XmlToken.Text(state.depth, text)
    }

    /**
     * Skips through the end of the next comment (i.e., `-->`).
     */
    private fun skipComment() {
        source.readThrough("-->", "looking for the end of a comment")
    }

    /**
     * Skips preprocessing instructions (e.g., `<?xml version='1.0'?>`) if any are found. Also skips spaces before/after
     * preprocessing instructions.
     */
    private fun skipPreprocessingInstructions() {
        skipSpace()

        while (source.advanceIf("<?")) {
            source.advanceUntilSpace() // e.g., `xml`
            skipSpace()

            while (!source.advanceIf("?>")) {
                readAttribute()
                skipSpace()
            }

            skipSpace()
        }
    }

    /**
     * Skips whitespaces.
     */
    private fun skipSpace() {
        source.advanceWhileSpace()
    }

    /**
     * Parses a string name into an [XmlToken.QualifiedName].
     */
    private fun String.qualify(): XmlToken.QualifiedName {
        val parts = split(':')
        if (parts.any(String::isEmpty)) error("Cannot understand qualified name '$this'")

        return when (parts.size) {
            1 -> XmlToken.QualifiedName(parts[0])
            2 -> XmlToken.QualifiedName(parts[1], parts[0])
            else -> error("Cannot understand qualified name '$this'")
        }
    }
}
