/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.serde.*
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CborDeserializerTest {
    @Test
    fun testNumberDeserializationThrowsOnOutOfRange() {
        val serializer = CborSerializer()
        serializer.serializeLong(Long.MAX_VALUE)
        serializer.serializeLong(Long.MAX_VALUE)
        serializer.serializeLong(Long.MAX_VALUE)
        serializer.serializeLong(Long.MAX_VALUE)

        val buffer = SdkBuffer().apply { write(serializer.toByteArray()) }

        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails { deserializer.deserializeInt() }
        assertFails { deserializer.deserializeShort() }
        assertFails { deserializer.deserializeByte() }
        assertEquals(Long.MAX_VALUE, deserializer.deserializeLong())
    }

    @Test
    fun testRecursionLimitingThrows() {
        // Indef map { "x": [0x81 × n, 0x00] } — "x" is unknown → skipValue → Value.decode recursion.
        val n = MAX_RECURSION_DEPTH + 1
        val p = ByteArray(1 + 2 + n + 1 + 1)
        var i = 0
        p[i++] = 0xbf.toByte() // indef map start
        p[i++] = 0x61
        p[i++] = 'x'.code.toByte() // text(1) "x"
        for (j in 0..<n) p[i++] = 0x81.toByte() // array(1) nested
        p[i++] = 0x00 // uint 0
        p[i++] = 0xff.toByte() // break

        val desc = SdkObjectDescriptor.Builder().build() // no fields → "x" is unknown

        val iter = CborDeserializer(p).deserializeStruct(desc)
        assertFailsWith<DeserializationRecursionException> {
            while (iter.findNextFieldIndex() != null) {
                iter.skipValue()
            }
        }
    }

    @Test
    fun testNestingAtExactLimitSucceeds() {
        // array(1) nested exactly MAX_RECURSION_DEPTH times, innermost contains uint 0
        val n = MAX_RECURSION_DEPTH
        val p = ByteArray(n + 1)
        for (i in 0..<n) p[i] = 0x81.toByte() // array(1)
        p[n] = 0x00 // uint 0

        val buffer = SdkBuffer().apply { write(p) }
        Value.decode(buffer)
    }

    /**
     * tt/P441722592/F9: A CBOR payload with an unknown field containing deeply nested arrays must throw
     * DeserializationRecursionException, not StackOverflowError. The depth check in Value.decode catches this before
     * the stack is exhausted.
     */
    @Test
    fun valueDecodeSkipRecursionThrowsRecursionException() {
        val n = MAX_RECURSION_DEPTH + 1
        val p = ByteArray(1 + 2 + n + 1 + 1)
        var i = 0
        p[i++] = 0xbf.toByte() // indef map start
        p[i++] = 0x61 // text(1)
        p[i++] = 'x'.code.toByte() // "x"
        for (j in 0..<n) p[i++] = 0x81.toByte() // array(1) nested
        p[i++] = 0x00 // uint 0
        p[i++] = 0xff.toByte() // break

        val desc = SdkObjectDescriptor.Builder().build() // no fields → "x" is unknown
        val iter = CborDeserializer(p).deserializeStruct(desc)

        assertFailsWith<DeserializationRecursionException> {
            while (iter.findNextFieldIndex() != null) {
                iter.skipValue()
            }
        }
    }

    /**
     * tt/P441722592/F10: A flat CBOR indef map with many null-valued known fields must not cause StackOverflowError.
     * The tailrec annotation on findNextFieldIndex ensures the null-skip path is compiled to a loop.
     */
    @Test
    fun findNextFieldIndexNullSkipDoesNotStackOverflow() {
        // CBOR indef map with n × (empty-string key, null value): 0xbf (0x60 0xf6)×n 0xff
        val n = 50_000
        val p = ByteArray(1 + 2 * n + 1)
        p[0] = 0xbf.toByte()
        for (i in 0..<n) {
            p[1 + 2 * i] = 0x60
            p[2 + 2 * i] = 0xf6.toByte()
        }
        p[p.size - 1] = 0xff.toByte()

        val desc = SdkObjectDescriptor.build {
            field(SdkFieldDescriptor(SerialKind.String, CborSerialName("")))
        }

        val iter = CborDeserializer(p).deserializeStruct(desc)
        // Should return null (all fields are null → all skipped) without StackOverflowError
        val result = iter.findNextFieldIndex()
        assertNull(result)
    }

    /**
     * Verify that deeply-nested `DecimalFraction` tags do not cause unbounded recursion / StackOverflowError.
     *
     * Payload structure (repeated n times):
     *   0xC4        — Tag(4) = DecimalFraction
     *   0x82        — List(2)
     *   0x00        — UInt(0) exponent
     *   [next level or terminal mantissa]
     *
     * Terminal mantissa: 0xC2 0x41 0x01 — Tag(2) BigNum, ByteString(1) [0x01]
     *
     * A decimal fraction's mantissa may only be an integer or a (neg) bignum tag, so `decodeDecimalFraction` rejects a
     * nested `DecimalFraction` mantissa immediately at the top level rather than recursing into it. The nesting is
     * therefore refused in O(1) with a [DeserializationException] and can never exhaust the stack — a stronger
     * guarantee than depth-limited recursion.
     */
    @Test
    fun nestedDecimalFractionTagsDoNotRecurse() {
        val n = MAX_RECURSION_DEPTH + 1
        // Each level: 0xC4 0x82 0x00 (3 bytes), terminal: 0xC2 0x41 0x01 (3 bytes)
        val p = ByteArray(n * 3 + 3)
        var i = 0
        for (j in 0..<n) {
            p[i++] = 0xC4.toByte() // Tag(4) DecimalFraction
            p[i++] = 0x82.toByte() // List(2)
            p[i++] = 0x00 // UInt(0) exponent
            // mantissa is the next level (or terminal)
        }
        // Terminal mantissa: BigNum tag wrapping ByteString
        p[i++] = 0xC2.toByte() // Tag(2) BigNum
        p[i++] = 0x41 // ByteString(1)
        p[i++] = 0x01 // byte value

        val buffer = SdkBuffer().apply { write(p) }
        // The outermost decimal fraction's mantissa is itself a Tag(4), which is not a valid mantissa → rejected here,
        // before any recursion into the nested levels.
        assertFailsWith<DeserializationException> {
            Value.decode(buffer)
        }
    }

    /**
     * Nested indefinite-length TextStrings bypass the depth limit because TextString.decode calls
     * IndefiniteList.decode(buffer) without a depth parameter (defaults to 0).
     *
     * Each indefinite text string level: 0x7F (indef string start) ... 0xFF (break)
     * Inside, Value.decode dispatches STRING major back to TextString.decode, which again
     * calls IndefiniteList.decode(buffer) with depth=0, creating unbounded recursion.
     *
     * Payload: n nested indefinite text strings, innermost contains a definite chunk "a".
     *   0x7F 0x7F 0x7F ... 0x61 0x61 ... 0xFF 0xFF 0xFF
     *
     * This should throw DeserializationRecursionException but currently causes StackOverflowError.
     */
    @Test
    fun indefiniteTextStringResetsDepthCounter() {
        val n = MAX_RECURSION_DEPTH + 1
        // n × 0x7F (indef text start) + definite chunk "a" (0x61 0x61) + n × 0xFF (break)
        val p = ByteArray(n + 2 + n)
        var i = 0
        for (j in 0..<n) p[i++] = 0x7F.toByte() // indefinite-length text string
        p[i++] = 0x61 // text(1)
        p[i++] = 0x61 // "a"
        for (j in 0..<n) p[i++] = 0xFF.toByte() // break

        val buffer = SdkBuffer().apply { write(p) }
        assertFailsWith<DeserializationRecursionException> {
            Value.decode(buffer)
        }
    }

    /**
     * Nested indefinite-length byte strings are structurally valid CBOR (each level is an indefinite byte string whose
     * only chunk is another indefinite byte string), so they are rejected purely on the recursion-depth limit rather
     * than as malformed input. This exercises the depth guard on the production `deserializeByteArray` path.
     *
     * Payload: n nested indefinite byte strings, innermost contains a definite 1-byte chunk.
     *   0x5F 0x5F 0x5F ... 0x41 0x01 ... 0xFF 0xFF 0xFF
     */
    @Test
    fun indefiniteByteStringDoesNotBypassDepthLimit() {
        val n = MAX_RECURSION_DEPTH + 1
        // n × 0x5F (indef byte string start) + definite chunk (0x41 0x01) + n × 0xFF (break)
        val p = ByteArray(n + 2 + n)
        var i = 0
        for (j in 0..<n) p[i++] = 0x5F.toByte() // indefinite-length byte string
        p[i++] = 0x41 // bytes(1)
        p[i++] = 0x01 // one byte
        for (j in 0..<n) p[i++] = 0xFF.toByte() // break

        val deserializer = CborPrimitiveDeserializer(SdkBuffer().apply { write(p) })
        assertFailsWith<DeserializationRecursionException> {
            deserializer.deserializeByteArray()
        }
    }
}
