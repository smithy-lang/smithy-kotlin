/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util.text.encoding

import aws.smithy.kotlin.runtime.InternalApi

private val ALPHA = (('A'..'Z') + ('a'..'z')).toSet()
private val DIGIT = ('0'..'9').toSet()
private val UNRESERVED = ALPHA + DIGIT + setOf('-', '.', '_', '~')
private val SUB_DELIMS = setOf('!', '$', '&', '\'', '(', ')', '*', ',', ';', '=')
private val VALID_UCHAR = UNRESERVED + SUB_DELIMS
private val VALID_PCHAR = VALID_UCHAR + setOf(':', '@')
private val VALID_FCHAR = VALID_PCHAR + setOf('/', '?')
private val VALID_QCHAR = VALID_FCHAR - setOf('&', '=')

@InternalApi
public interface Encoding {
    @InternalApi
    public companion object {
        public val UserInfo: Encoding = PercentEncoding("user info", VALID_UCHAR)
        public val Path: Encoding = PercentEncoding("path", VALID_PCHAR)
        public val Query: Encoding = PercentEncoding("query string", VALID_QCHAR, mapOf(' ' to '+'))
        public val Fragment: Encoding = PercentEncoding("fragment", VALID_FCHAR)

        internal val None = object : Encoding {
            override val name = "(no encoding)"
            override fun decode(encoded: String) = encoded
            override fun encode(decoded: String) = decoded
        }
    }

    public val name: String

    public fun decode(encoded: String): String
    public fun encode(decoded: String): String

    public fun encodableFromDecoded(decoded: String): Encodable = Encodable(decoded, encode(decoded), this)
    public fun encodableFromEncoded(encoded: String): Encodable {
        val decoded = decode(encoded)
        val reencoded = encode(decoded) // TODO is this right?
        return Encodable(decoded, reencoded, this)
    }
}

@InternalApi
public class PercentEncoding(
    override val name: String,
    public val validChars: Set<Char>,
    public val specialMapping: Map<Char, Char> = mapOf(),
) : Encoding {
    @InternalApi
    public companion object {
        private const val UPPER_HEX = "0123456789ABCDEF"

        private fun percentAsciiEncode(char: Char) = buildString {
            val value = char.code and 0xff
            append('%')
            append(UPPER_HEX[value shr 4])
            append(UPPER_HEX[value and 0x0f])
        }

        public fun StringBuilder.percentEncode(byte: Byte) {
            val value = byte.toInt() and 0xff
            append('%')
            append(UPPER_HEX[value shr 4])
            append(UPPER_HEX[value and 0x0f])
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private val asciiMapping = (0..<128)
        .map(Int::toChar)
        .filterNot(validChars::contains)
        .associateWith(::percentAsciiEncode)

    private val encodeMap = asciiMapping + specialMapping.mapValues { (_, char) -> char.toString() }

    private val decodeMap = (validChars.associateWith { it } + specialMapping)
        .entries
        .associate { (decoded, encoded) -> encoded to decoded }

    override fun decode(encoded: String): String = buildString(encoded.length) {
        var byteBuffer: ByteArray? = null // Do not initialize unless needed

        var i = 0
        var c: Char
        while (i < encoded.length) {
            c = encoded[i]
            if (c == '%') {
                if (byteBuffer == null) {
                    byteBuffer = ByteArray((encoded.length - i) / 3) // Max remaining percent-encoded bytes
                }

                var byteCount = 0
                while ((i + 2) < encoded.length && c == '%') {
                    val byte = encoded.substring(i + 1, i + 3).toIntOrNull(radix = 16)?.toByte() ?: break
                    byteBuffer[byteCount++] = byte

                    i += 3
                    if (i < encoded.length) c = encoded[i]
                }

                append(byteBuffer.decodeToString(endIndex = byteCount))

                if (i != encoded.length && c == '%') {
                    append(c)
                    i++
                }
            } else {
                append(decodeMap[c] ?: throw IllegalArgumentException("unknown encoding, cannot decode character '$c'"))
                i++
            }
        }
    }

    override fun encode(decoded: String): String = buildString(decoded.length) {
        val bytes = decoded.encodeToByteArray()
        for (byte in bytes) {
            val char = byte.toInt().toChar()
            if (char in validChars) {
                append(char)
            } else {
                encodeMap[char]?.let(::append) ?: percentEncode(byte)
            }
        }
    }
}
