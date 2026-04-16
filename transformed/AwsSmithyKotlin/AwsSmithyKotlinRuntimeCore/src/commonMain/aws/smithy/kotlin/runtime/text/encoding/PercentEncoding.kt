/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.text.encoding

import aws.smithy.kotlin.runtime.InternalApi

/**
 * An algorithm that percent-encodes string data for use in URLs
 * @param name The name of this encoding
 * @param validChars The set of characters which are valid _unencoded_ (i.e., in plain text). All other characters will
 * be percent-encoded unless they appear in [specialMapping].
 * @param specialMapping A mapping of characters to their special (i.e., non-percent-encoded) form. The characters which
 * are keys in this map will not be percent-encoded.
 */
@InternalApi
public class PercentEncoding(
    override val name: String,
    public val validChars: Set<Char>,
    public val specialMapping: Map<Char, Char> = mapOf(),
) : Encoding {
    @InternalApi
    public companion object {
        // These definitions are from RFC 3986 Appendix A, see https://datatracker.ietf.org/doc/html/rfc3986#appendix-A
        private val ALPHA = (('A'..'Z') + ('a'..'z')).toSet()
        private val DIGIT = ('0'..'9').toSet()
        private val UNRESERVED = ALPHA + DIGIT + setOf('-', '.', '_', '~')
        private val SUB_DELIMS = setOf('!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=')
        private val VALID_UCHAR = UNRESERVED + SUB_DELIMS
        private val VALID_PCHAR = VALID_UCHAR + setOf(':', '@')
        private val VALID_FCHAR = VALID_PCHAR + setOf('/', '?')

        private val VALID_QCHAR = VALID_FCHAR - setOf('&', '=') // UNRESERVED

        // https://smithy.io/2.0/spec/http-bindings.html#httplabel-serialization-rules
        private val SMITHY_LABEL_CHAR = UNRESERVED

        // SigV4 is very strict on what MUST be encoded in queries:
        // https://docs.aws.amazon.com/IAM/latest/UserGuide/create-signed-request.html
        private val SIGV4_SIGNING_CHAR = UNRESERVED

        private const val UPPER_HEX = "0123456789ABCDEF"

        /**
         * A [PercentEncoding] instance suitable for encoding host names/addresses
         */
        public val Host: Encoding = PercentEncoding("host", UNRESERVED + ':') // e.g., for IPv6 zone ID encoding

        /**
         * A [PercentEncoding] instance suitable for encoding userinfo
         */
        public val UserInfo: Encoding = PercentEncoding("user info", VALID_UCHAR)

        /**
         * A [PercentEncoding] instance suitable for encoding URL paths
         */
        public val Path: Encoding = PercentEncoding("path", VALID_PCHAR)

        /**
         * A [PercentEncoding] instance suitable for encoding query strings
         */
        public val Query: Encoding = PercentEncoding("query string", VALID_QCHAR)

        /**
         * A [PercentEncoding] instance suitable for encoding URL fragments
         */
        public val Fragment: Encoding = PercentEncoding("fragment", VALID_FCHAR)

        /**
         * A [PercentEncoding] instance suitable for encoding Smithy-specific `application/x-www-form-urlencoded` data
         */
        public val FormUrl: Encoding = PercentEncoding("form URL", UNRESERVED)

        /**
         * A [PercentEncoding] instance suitable for encoding values into Smithy labels
         */
        public val SmithyLabel: Encoding = PercentEncoding("Smithy label", SMITHY_LABEL_CHAR)

        /**
         * A [PercentEncoding] instance suitable for encoding values used during SigV4 canonicalization and signature
         * creation
         */
        public val SigV4: Encoding = PercentEncoding("SigV4", SIGV4_SIGNING_CHAR)

        private fun percentAsciiEncode(char: Char) = buildString {
            val value = char.code and 0xff
            append('%')
            append(UPPER_HEX[value shr 4])
            append(UPPER_HEX[value and 0x0f])
        }

        private fun StringBuilder.percentEncode(byte: Byte) {
            val value = byte.toInt() and 0xff
            append('%')
            append(UPPER_HEX[value shr 4])
            append(UPPER_HEX[value and 0x0f])
        }
    }

    private val asciiMapping = (0..<128)
        .map(Int::toChar)
        .filterNot(validChars::contains)
        .associateWith(Companion::percentAsciiEncode)

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
                append(decodeMap[c] ?: c)
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
