/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor.encoding

import aws.smithy.kotlin.runtime.io.SdkBufferedSource
import aws.smithy.kotlin.runtime.serde.DeserializationException
import aws.smithy.kotlin.runtime.serde.DeserializationRecursionException
import aws.smithy.kotlin.runtime.serde.cbor.nextValueIsIndefiniteBreak

/**
 * Advance [buffer] past exactly one encoded CBOR value without materializing it.
 */
internal fun skipValue(buffer: SdkBufferedSource, depth: Int = 0) {
    DeserializationRecursionException.assertDepth(depth)

    val head = peekByte(buffer)
    val minor = minorOf(head)

    when (majorOf(head)) {
        Major.U_INT, Major.NEG_INT -> decodeArgument(buffer)

        Major.BYTE_STRING, Major.STRING -> if (minor == Minor.INDEFINITE.value) {
            skipUntilBreak(buffer, depth)
        } else {
            buffer.skip(decodeArgument(buffer).toLong())
        }

        Major.LIST -> if (minor == Minor.INDEFINITE.value) {
            skipUntilBreak(buffer, depth)
        } else {
            val length = decodeArgument(buffer).toLong()
            for (i in 0 until length) skipValue(buffer, depth + 1)
        }

        Major.MAP -> if (minor == Minor.INDEFINITE.value) {
            skipUntilBreak(buffer, depth)
        } else {
            val length = decodeArgument(buffer).toLong()
            for (i in 0 until length * 2) skipValue(buffer, depth + 1)
        }

        Major.TAG -> {
            decodeArgument(buffer) // consume the tag id
            skipValue(buffer, depth + 1)
        }

        Major.TYPE_7 -> when (minor) {
            Minor.TRUE.value, Minor.FALSE.value, Minor.NULL.value, Minor.UNDEFINED.value -> buffer.skip(1L)
            Minor.FLOAT16.value -> buffer.skip(3L)
            Minor.FLOAT32.value -> buffer.skip(5L)
            Minor.FLOAT64.value -> buffer.skip(9L)
            else -> throw DeserializationException("Unexpected type 7 minor value $minor")
        }
    }
}

// Skip the elements of an indefinite-length container up to and including the break marker.
private fun skipUntilBreak(buffer: SdkBufferedSource, depth: Int) {
    buffer.readByte() // discard the indefinite-length head
    while (!buffer.nextValueIsIndefiniteBreak) {
        skipValue(buffer, depth + 1)
    }
    buffer.readByte() // discard the break marker
}
