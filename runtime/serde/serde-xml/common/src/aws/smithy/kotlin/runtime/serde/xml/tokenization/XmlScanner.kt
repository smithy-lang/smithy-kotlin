/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde.xml.tokenization

import aws.smithy.kotlin.runtime.serde.DeserializationException
import aws.smithy.kotlin.runtime.serde.xml.XmlToken
import aws.smithy.kotlin.runtime.util.text.codePointToChars

// https://www.w3.org/TR/xml/#NT-S
private val xmlWhitespaceChars = setOf(
    '\t', // horizontal tab
    '\r', // carriage return
    '\n', // newline
    ' ', // normal space
)
private fun Char.isXmlWhitespace() = xmlWhitespaceChars.contains(this)

private fun Char.toRange() = this..this

// https://www.w3.org/TR/xml/#NT-Name
private val nameStartCharRanges = setOf(
    ':'.toRange(),
    'A'..'Z',
    '_'.toRange(),
    'a'..'z',
    '\u00C0'..'\u00D6',
    '\u00D8'..'\u00F6',
    '\u00F8'..'\u02FF',
    '\u0370'..'\u037D',
    '\u037F'..'\u1FFF',
    '\u200C'..'\u200D',
    '\u2070'..'\u218F',
    '\u2C00'..'\u2FEF',
    '\u3001'..'\uD7FF',
)
private val nameCharRanges = nameStartCharRanges + setOf(
    '-'.toRange(),
    '.'.toRange(),
    '0'..'9',
    '\u00B7'.toRange(),
    '\u0300'..'\u036f',
    '\u203f'..'\u2040',
)

// TODO these probably aren't very performant. We may be able to improve efficiency with a treeset of ranges
private fun Char.isValidForNameStart(): Boolean = nameStartCharRanges.any { it.contains(this) }
private fun Char.isValidForName(): Boolean = nameCharRanges.any { it.contains(this) }

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
 * A stateful scanner that scans a [StringTextStream] and reads [XmlToken] elements.
 */
class XmlScanner(internal val source: StringTextStream) {
    /**
     * The active state of this scanner.
     */
    var state: ScannerState = ScannerState.Initial
        private set

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
        ScannerState.EndOfDocument -> null

        is ScannerState.Tag.EmptyTag -> {
            state = currentState.parent ?: ScannerState.EndOfDocument
            // In this case we don't actually need to read more from the source because an empty tag is represented as
            // a BeginElement (which was previously returned) and an EndElement (which will be returned now).
            XmlToken.EndElement(currentState.depth, currentState.name)
        }

        ScannerState.Initial -> {
            skipPreprocessingInstructions()
            state = ScannerState.BeforeRootTag
            parseNext()
        }

        ScannerState.BeforeRootTag -> {
            skipSpace()
            readTagToken()
        }

        is ScannerState.Tag.OpenTag ->
            if (source.peekMatches("<") && !source.peekMatches("<![CDATA[")) {
                readTagToken()
            } else {
                readTextToken()
            }
    }

    /**
     * Reads an attribute key and value from the source.
     */
    private fun readAttribute(): Pair<XmlToken.QualifiedName, String> {
        val name = readName()
        skipSpace()

        val equals = source.readOrThrow { "Unexpected end-of-doc while trying to read attribute equals" }
        if (equals != '=') error("Unexpected '$equals' while trying to read attribute equals")
        skipSpace()

        val value = readQuoted()

        return name to value
    }

    /**
     * Reads a CDATA section from the source. This assumes that the source position is at the start of the `<![CDATA[`
     * section.
     */
    private fun readCdata(): String {
        val cdataStart = source.readOrThrow(9) { "Unexpected end-of-doc while trying to read CDATA" }
        if (cdataStart != "<![CDATA[") error("Unexpected characters while trying to read start of CDATA")

        val cdataBodyAndEnd = source.readThrough("]]>") { "Unexpected end-of-doc while trying to read CDATA content" }
        return cdataBodyAndEnd.substring(0, cdataBodyAndEnd.length - 3) // Strip off trailing "]]>"
    }

    /**
     * Reads a tag or attribute name from the source.
     */
    private fun readName(): XmlToken.QualifiedName {
        val start = source.readOrThrow { "Unexpected end-of-doc while trying to read name" }
        if (!start.isValidForNameStart()) error("Invalid start character for name '$start'")
        val subsequent = source.readWhile { it.isValidForName() }
        return "$start$subsequent".qualify()
    }

    /**
     * Reads a quoted string from the source. The quotes may be single (') or double (").
     */
    private fun readQuoted(): String {
        val quoteChar = source.readOrThrow { "Unexpected end-of-doc while trying to read attribute value" }
        if (quoteChar != '\'' && quoteChar != '"') {
            error("Unexpected '$quoteChar' while trying to read attribute value")
        }

        return buildString {
            while (true) {
                when (val c = source.readOrThrow { "Unexpected end-of-doc while trying to read a string" }) {
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
        val ref = source
            .readThrough(";") { "Unexpected end-of-doc while trying to read a char/entity reference" }
            .trimEnd(';')

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
        val lt = source.readOrThrow { "Unexpected end of document while looking for the start of a tag" }
        if (lt != '<') error("Unexpected character '$lt' while looking for the start of a tag")

        if (source.peekMatches("!--")) {
            skipComment()
            return parseNext()!!
        }

        val token = if (source.peekMatches("/")) {
            // Skip the '/'
            source.readOrThrow { "Unexpected end-of-doc while looking for the end of a tag" }

            val openTagState = state as ScannerState.Tag.OpenTag
            val expectedName = openTagState.name
            val actualName = readName()
            if (actualName != expectedName) {
                error("Unexpected '/$actualName' tag while looking for '/$expectedName' tag")
            }

            skipSpace()

            val ch = source.readOrThrow { "Unexpected end-of-doc while looking for the end of a tag" }
            if (ch != '>') error("Unexpected character '$ch' while looking for the end of a tag")

            state = openTagState.parent ?: ScannerState.EndOfDocument
            XmlToken.EndElement(openTagState.depth, actualName)
        } else {
            val openTagState = (state as? ScannerState.Tag.OpenTag)?.copy(seenChildren = true)

            val name = readName()
            skipSpace()

            val allAttributes = mutableMapOf<XmlToken.QualifiedName, String>()
            var nextCh: Char
            while (true) {
                nextCh = source.peekOrThrow { "Unexpected end-of-doc while looking for the end of a tag" }
                when (nextCh) {
                    '/', '>' -> break
                    else -> allAttributes += readAttribute()
                }
                skipSpace()
            }

            val (attributes, nsDeclarations) = allAttributes.extractNsDeclarations()

            val nextState = if (nextCh == '>') {
                // Skip the '>'
                source.readOrThrow { "Unexpected end-of-doc while looking for the end of a tag" }
                ScannerState.Tag.OpenTag(name, openTagState, false)
            } else { // '/'
                val slashClose = source.readOrThrow(2) { "Unexpected end-of-doc while looking for the end of a tag" }
                if (slashClose != "/>") error("Unexpected characters while looking for the end of a tag")
                ScannerState.Tag.EmptyTag(name, openTagState)
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
        val text = buildString {
            var nextCh: Char
            while (true) {
                nextCh = source.peekOrThrow { "Unexpected end-of-doc while reading text node" }
                when (nextCh) {
                    '<' -> when {
                        source.peekMatches("<!--") -> skipComment()
                        source.peekMatches("<![CDATA[") -> append(readCdata())
                        else -> break
                    }

                    '&' -> {
                        // Skip the '&'
                        source.readOrThrow { "Unexpected end-of-doc while reading a char/ent reference" }
                        append(readReference())
                    }

                    else -> {
                        // Consume the character
                        source.readOrThrow { "Unexpected end-of-doc while reading text node" }
                        append(nextCh)
                    }
                }
            }
        }

        val openTagState = state as ScannerState.Tag.OpenTag
        val openTagIsMostRecent = openTagState.seenChildren
        val closeTagIsNext = source.peekMatches("</${openTagState.name}")

        state = openTagState.copy(seenChildren = true)

        // Return a blank text node only if it's the only node in this tag; otherwise, skip to the next token
        if (text.isBlank() && (openTagIsMostRecent || !closeTagIsNext)) {
            return parseNext()!!
        }

        return XmlToken.Text(state.depth, text)
    }

    /**
     * Skips through the end of the next comment (i.e., `-->`).
     */
    private fun skipComment() {
        source.readThrough("-->") { "Unexpected end-of-doc while looking for the end of a comment" }
    }

    /**
     * Skips preprocessing instructions (e.g., `<?xml version='1.0'?>`) if any are found. Also skips spaces before/after
     * preprocessing instructions.
     */
    private fun skipPreprocessingInstructions() {
        skipSpace()

        while (source.peekMatches("<?")) {
            source.readWhile { !it.isXmlWhitespace() } // e.g., '<?xml'
            skipSpace()

            while (!source.peekMatches("?>")) {
                readAttribute()
                skipSpace()
            }

            // Skip the '?>'
            source.readOrThrow(2) { "Unexpected end-of-doc while looking for the end of a processing instruction" }
            skipSpace()
        }
    }

    /**
     * Skips whitespaces.
     */
    private fun skipSpace() {
        source.readWhile { it.isXmlWhitespace() }
    }

    /**
     * Parses a string name into an [XmlToken.QualifiedName].
     */
    private fun String.qualify(): XmlToken.QualifiedName {
        if (isBlank()) error("Cannot parse blank name")

        val parts = split(':')
        if (parts.any(String::isEmpty)) error("Cannot understand qualified name '$this'")

        return when (parts.size) {
            1 -> XmlToken.QualifiedName(parts[0])
            2 -> XmlToken.QualifiedName(parts[1], parts[0])
            else -> error("Cannot understand qualified name '$this'")
        }
    }
}
