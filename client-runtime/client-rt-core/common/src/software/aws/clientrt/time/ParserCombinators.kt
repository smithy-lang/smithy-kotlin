/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.time

import software.aws.clientrt.SdkBaseException

// mini parser combinator library

/**
 * Holds the result of invoking a [Parser]
 * @property pos The current position parsed to (i.e. the next position to continue parsing the input from)
 * @property result The result type of the parse function
 */
data class ParseResult<out T>(val pos: Int, val result: T)

// takes the input string and the current position to parse from, must return a tuple of (nestPosition, parsed result)
typealias Parser<T> = (str: String, pos: Int) -> ParseResult<T>

/**
 * Base parsing exception
 * @param input The input being parsed
 * @param message Additional contextual message around what the failure was
 * @param position The position where the parse failure happened
 */
open class ParseException(input: String, message: String, position: Int) :
    SdkBaseException("parse `$input`: error at $position: $message")

// internal marker exception
class TakeWhileMNException(input: String, val lastPos: Int, val expected: Int, val matched: Int) : ParseException(input, "expected at least $expected matches of predicate; only matched $matched", lastPos)

/**
 * Contains information on needed data if a parser throws [IncompleteException]
 */
sealed class Needed {
    object Unknown : Needed() {
        override fun toString(): String = "incomplete input"
    }

    data class Size(val size: Int) : Needed() {
        override fun toString(): String = "incomplete input needed ($size)"
    }
}

/**
 * Thrown when end of input is reached and parser still expects additional characters
 *
 * @param input The input string being parsed
 * @param needed Description of the number of chars still needed (if known)
 */
class IncompleteException(input: String, val needed: Needed) : ParseException(input, needed.toString(), input.length - 1)

// parser precondition check. Ensure that the current position is in bounds of the input
internal fun precond(input: String, pos: Int, need: Int = 0) {
    val invalidRead = if (need == 0) pos >= input.length else pos + need > input.length
    if (invalidRead) {
        val needed = when (need) {
            0 -> Needed.Unknown
            else -> Needed.Size(need)
        }
        throw IncompleteException(input, needed)
    }
}

/**
 * Test whether the input character is a digit or not
 */
fun isDigit(chr: Char): Boolean = chr in '0'..'9'

/**
 * Parse a literal character
 */
internal fun char(chr: Char): Parser<Char> = { str, pos ->
    precond(str, pos, 1)
    val c = str[pos]
    if (c != chr) throw ParseException(str, "expected `$chr` found `$c`", pos)
    ParseResult(pos + 1, c)
}

/**
 * Parse one of the given characters, first match wins
 */
internal fun oneOf(chars: String): Parser<Char> = { str, pos ->
    precond(str, pos, 1)
    var result: ParseResult<Char>? = null
    val c = str[pos]
    for (chr in chars) {
        if (chr == c) {
            result = ParseResult(pos + 1, chr)
            break
        }
    }
    result ?: throw ParseException(str, "expected one of `$chars` found $c", pos)
}

/**
 * Parse and consume the chars in [match]
 */
internal fun tag(match: String): Parser<String> = { str, pos ->
    precond(str, pos, match.length)

    for ((idx, chr) in match.withIndex()) {
        val i = pos + idx
        if (str[i] != chr) {
            throw ParseException(str, "expected `$match` found `${str.substring(pos, pos + match.length)}`", pos)
        }
    }

    ParseResult(pos + match.length, match)
}

/**
 * Returns the largest input range until the [predicate] returns true
 */
internal fun takeTill(predicate: (Char) -> Boolean): Parser<IntRange> = { str, pos ->
    precond(str, pos)
    var i = pos
    while (i < str.length && !predicate(str[i])) { i++ }

    if (i == str.length) throw IncompleteException(str, Needed.Unknown)

    ParseResult(i, pos until i)
}

/**
 * Return the longest (m <= len <= n) input range that matches the predicate
 * @param m minimum number of times the predicate must match or parsing fails
 * @param n maximum number of times the predicate is allowed to match before returning
 */
internal fun takeWhileMN(m: Int, n: Int, predicate: (Char) -> Boolean): Parser<IntRange> = { str, pos ->
    require(n >= m) { "min m=$m cannot be greater than max=$n" }
    precond(str, pos)

    var i = pos
    while (i < str.length && (i - pos < n) && predicate(str[i])) { i++ }

    val cnt = i - pos
    if (cnt < m) {
        if (i >= str.length) {
            throw IncompleteException(str, Needed.Size(m - cnt))
        } else {
            throw TakeWhileMNException(str, i, m, cnt)
        }
    }

    ParseResult(i, IntRange(pos, i - 1))
}

private fun fmtTakeWhileErrorMsg(m: Int, n: Int, expected: Int, msg: String): String {
    val modifier = if (m == n) "exactly" else "at least"
    return "expected $modifier $expected digits; $msg"
}

/**
 * take (m <= len <= n) digits from the input and convert to a number using the provided [transform]
 * @param m minimum number of digits that must be matched or parsing fails
 * @param n maximum number of digits to match
 */
internal fun <T : Number> takeMNDigitsT(m: Int, n: Int, transform: (String, IntRange) -> T): Parser<T> = { str, pos ->
    precond(str, pos)
    try {
        val (pos1, range) = takeWhileMN(m, n, ::isDigit)(str, pos)
        if (range.isEmpty()) {
            throw ParseException(str, "expected integer", pos)
        }

        val res = transform(str, range)
        ParseResult(pos1, res)
    } catch (ex: TakeWhileMNException) {
        val msg = fmtTakeWhileErrorMsg(m, n, ex.expected, "found ${ex.matched}")
        throw ParseException(str, msg, pos)
    } catch (ex: IncompleteException) {
        val msg = fmtTakeWhileErrorMsg(m, n, m, "${ex.needed}")
        throw ParseException(str, msg, pos)
    }
}

/**
 * take (m <= len <= n) digits from the input and convert to an [Int]
 * @param m minimum number of digits that must be matched or parsing fails
 * @param n maximum number of digits to match
 */
internal fun takeMNDigitsInt(m: Int, n: Int): Parser<Int> = takeMNDigitsT(m, n) { str, range ->
    str.substring(range).toInt()
}

/**
 * take (m <= len <= n) digits from the input and convert to a [Long]
 * @param m minimum number of digits that must be matched or parsing fails
 * @param n maximum number of digits to match
 */
internal fun takeMNDigitsLong(m: Int, n: Int): Parser<Long> = takeMNDigitsT(m, n) { str, range ->
    str.substring(range).toLong()
}

/**
 * take exactly n digits and convert to an integer
 * @param n the exact number of digits to match
 */
internal fun takeNDigits(n: Int): Parser<Int> = takeMNDigitsInt(n, n)

/**
 * Like [takeMNDigitsInt] but verifies the result is inside of the given range
 */
internal fun mnDigitsInRange(m: Int, n: Int, range: IntRange): Parser<Int> = { str, pos ->
    precond(str, pos)
    val (pos0, result) = takeMNDigitsInt(m, n)(str, pos)
    if (!range.contains(result)) {
        throw ParseException(str, "$result not in range $range", pos)
    }

    ParseResult(pos0, result)
}

/**
 * Like [takeNDigits] but verifies the result is inside of the given range
 */
internal fun nDigitsInRange(n: Int, range: IntRange): Parser<Int> = mnDigitsInRange(n, n, range)

/**
 * take (m <= len <= n) digits as if they come after the decimal point. Return the result as a whole number
 * up to the number of digits in [denomDigits]
 */
internal fun fraction(m: Int, n: Int, denomDigits: Int): Parser<Int> = { str, pos ->
    require(denomDigits <= n) { "denomDigits ($denomDigits) must be less than max=$n digits to parse" }
    precond(str, pos)

    val (pos0, range) = takeWhileMN(m, n, ::isDigit)(str, pos)
    if (range.isEmpty()) {
        throw ParseException(str, "expected integer", pos)
    }

    var result = str.substring(range).toInt()
    val parsed = range.last - range.first
    for (i in parsed until denomDigits - 1) {
        result *= 10
    }

    ParseResult(pos0, result)
}

/**
 * Optionally apply the [parser] to the input. Return the result on successful match (parse) or else null without
 * advancing the current position
 */
internal fun <T> optional(parser: Parser<T>): Parser<T?> = { str, pos ->
    try {
        parser(str, pos)
    } catch (_: ParseException) {
        ParseResult(pos, null)
    }
}

/**
 * Return the result of the parser on success or the value [or] when the parser fails
 */
internal fun <T> optionalOr(parser: Parser<T>, or: T): Parser<T> = map(optional(parser)) { it ?: or }

/**
 * Try a list of parsers and return the result of the first successful match
 */
internal fun <T> alt(vararg parsers: Parser<T>): Parser<T> = { str, pos ->
    var res: ParseResult<T>? = null
    for (p in parsers) {
        try {
            res = p(str, pos)
            break
        } catch (_: ParseException) {
            continue
        }
    }

    res ?: throw ParseException(str, "no alternatives matched", pos)
}

/**
 * Return the result of the second parser if the result of the first parser was successful
 *
 * ```
 * preceded(char('-'), takeNDigits(3))("-200", 0) == ParseResult(4, 200)
 * ```
 */
internal fun <T, S> preceded(pre: Parser<T>, post: Parser<S>): Parser<S> = { str, pos ->
    val res = pre(str, pos)
    post(str, res.pos)
}

/**
 * Map the result of the parser function by returning the result of invoking [block] as the parse result
 */
internal fun <T, S> map(parser: Parser<T>, block: (T) -> S): Parser<S> = { str, pos ->
    val (pos0, t) = parser(str, pos)
    val s = block(t)
    ParseResult(pos0, s)
}

/**
 * Calls the [parser] if the [condition] is true and returns the result, otherwise maps to null
 */
internal fun <T> cond(condition: Boolean, parser: Parser<T>): Parser<T?> = { str, pos ->
    if (condition) {
        parser(str, pos)
    } else {
        ParseResult(pos, null)
    }
}

/**
 * Call this parser first and then the [next] parser with the (position) result of the first.
 * Returns a Pair with the result of invoking both parser sequentially
 */
internal fun <T, S> Parser<T>.then(next: Parser<S>): Parser<Pair<T, S>> = { str, pos ->
    val (pos0, t) = this(str, pos)
    val (pos1, s) = next(str, pos0)
    ParseResult(pos1, Pair(t, s))
}
