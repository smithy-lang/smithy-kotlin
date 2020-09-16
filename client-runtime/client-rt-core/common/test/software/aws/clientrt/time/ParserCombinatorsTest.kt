/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.time

import io.kotest.matchers.string.shouldContain
import kotlin.test.*

class ParsersTest {

    @Test
    fun `test takeWhileMN`() {
        assertEquals(ParseResult(5, IntRange(0, 4)), takeWhileMN(5, 5, ::isDigit)("12345abcde", 0))
        assertEquals(ParseResult(2, IntRange(0, 1)), takeWhileMN(1, 2, ::isDigit)("12345abcde", 0))

        assertEquals(ParseResult(3, IntRange(0, 2)), takeWhileMN(1, 5, ::isDigit)("123", 0))

        // empty range
        val emptyResult = takeWhileMN(0, 3, ::isDigit)("a123", 0)
        assertTrue(emptyResult.result.isEmpty())
        assertEquals(0, emptyResult.pos)

        // cnt < min
        val ex = assertFails {
            takeWhileMN(4, 5, ::isDigit)("123a", 0)
        }
        ex.message!!.shouldContain("error at 3: expected at least 4 matches of predicate; only matched 3")

        // incomplete
        val ex2 = assertFails {
            takeWhileMN(4, 5, ::isDigit)("123", 0)
        }
        ex2.message!!.shouldContain("error at 2: incomplete input needed (1)")
    }

    @Test
    fun `test takeMNDigits`() {
        assertEquals(ParseResult(2, 22), takeNDigits(2)("22", 0))
        assertEquals(ParseResult(8, 227), takeMNDigitsInt(2, 3)("abcde22759fge", 5))
        assertEquals(ParseResult(14, 123456789), takeMNDigitsInt(1, 9)("abcde123456789fge", 5))

        // incomplete
        var ex = assertFailsWith<ParseException> { takeNDigits(3)("abcde22", 5) }
        ex.message!!.shouldContain("error at 5: expected exactly 3 digits; incomplete input needed (1)")

        // non-digit
        ex = assertFailsWith<ParseException> { takeMNDigitsInt(1, 2)("1e22", 1) }
        ex.message!!.shouldContain("error at 1: expected at least 1 digits; found 0")
    }

    @Test
    fun `test mnDigitsInRange`() {
        assertEquals(ParseResult(2, 22), nDigitsInRange(2, IntRange(0, 30))("22", 0))
        // boundary of range
        assertEquals(ParseResult(8, 227), mnDigitsInRange(2, 3, IntRange(227, 227))("abcde22759fge", 5))

        // out of range
        var ex = assertFailsWith<ParseException> { mnDigitsInRange(1, 2, IntRange(0, 11))("12", 0) }
        ex.message!!.shouldContain("error at 0: 12 not in range 0..11")

        // empty range
        ex = assertFailsWith<ParseException> { mnDigitsInRange(1, 2, IntRange(12, 11))("15", 0) }
        ex.message!!.shouldContain("error at 0: 15 not in range 12..11")
    }

    @Test
    fun `test chars`() {
        assertEquals('x', char('x')("abcxyz", 3).result)

        val ex = assertFailsWith<ParseException> { char('x')("abcxyz", 2) }
        ex.message!!.shouldContain("error at 2: expected `x` found `c`")
    }

    @Test
    fun `test tag`() {
        assertEquals(ParseResult(5, "xy"), tag("xy")("abcxyz", 3))

        val ex = assertFailsWith<ParseException> { tag("bcxz")("abcxyz", 1) }
        ex.message!!.shouldContain("error at 1: expected `bcxz` found `bcxy`")

        val ex2 = assertFailsWith<IncompleteException> { tag("bcxz")("bcx", 0) }
        ex2.message!!.shouldContain("error at 2: incomplete input needed (4)")
    }

    @Test
    fun `test oneOf`() {
        assertEquals('x', oneOf("yzxa")("abcxyz", 3).result)

        var ex = assertFailsWith<ParseException> { oneOf("yzxa")("abcxyz", 2) }
        ex.message!!.shouldContain("error at 2: expected one of `yzxa` found c")
    }

    @Test
    fun `test fraction`() {
        assertEquals(ParseResult(1, 3), fraction(1, 1, 1)("345", 0))

        assertEquals(ParseResult(3, 345), fraction(1, 3, 3)("345", 0))
        assertEquals(ParseResult(3, 345000), fraction(1, 6, 6)("345", 0))
        assertEquals(ParseResult(2, 320000), fraction(1, 6, 6)("32", 0))

        // e.g. ns
        assertEquals(ParseResult(9, 1), fraction(1, 9, 9)("000000001", 0))
        assertEquals(ParseResult(9, 100), fraction(1, 9, 9)("000000100", 0))
    }

    @Test
    fun `test takeTill`() {
        assertEquals(ParseResult(5, IntRange(0, 4)), takeTill(::isDigit)("abcde12345", 0))
        assertEquals(ParseResult(5, IntRange(2, 4)), takeTill(::isDigit)("abcde1", 2))

        // incomplete
        val ex = assertFails {
            takeTill(::isDigit)("abcdexyz", 0)
        }
        ex.message!!.shouldContain("error at 7: incomplete input")
    }
}

class CombinatorTest {
    @Test
    fun `test optional`() {
        assertEquals(ParseResult(0, null), optional(char('x'))("abc", 0))
        // should consume a character
        assertEquals(ParseResult(1, 'x'), optional(char('x'))("xyz", 0))
    }

    @Test
    fun `test alt`() {
        assertEquals(ParseResult(1, 'y'), alt(char('x'), char('y'))("yup", 0))

        val ex = assertFails {
            alt(char('x'), char('y'))("abc", 0)
        }
        ex.message!!.shouldContain("no alternatives matched")
    }

    @Test
    fun `test preceded`() {
        assertEquals(ParseResult(2, 'y'), preceded(char('x'), char('y'))("xyz", 0))

        // with optional
        val res = optional(preceded(char('.'), takeNDigits(2)))("abc.22345", 3)
        assertEquals(ParseResult(6, 22), res)
    }

    @Test
    fun `test map`() {
        val result = map(alt(char('+'), char('-'))) {
            when (it) {
                '-' -> -1
                else -> 1
            }
        }("+200", 0)
        assertEquals(ParseResult(1, 1), result)
    }

    @Test
    fun `test cond`() {
        assertEquals(ParseResult(0, null), cond(false, char('x'))("abc", 0))
        // should consume a character
        assertEquals(ParseResult(1, 'x'), cond(true, char('x'))("xyz", 0))
    }

    @Test
    fun `test then`() {
        assertEquals(ParseResult(2, Pair('x', 'y')), char('x').then(char('y'))("xyz", 0))
    }
}
