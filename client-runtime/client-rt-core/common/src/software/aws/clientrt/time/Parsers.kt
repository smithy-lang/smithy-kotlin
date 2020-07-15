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

// timestamp format parsers

/**
 * Represents the result of parsing a date from a string in some known format (e.g. ISO-8601, RFC-822, etc)
 */
private data class ParsedDate(val year: Int, val month: Int, val day: Int)

/**
 * Represents the result of parsing the time and optionally the offset in seconds in some known format
 */
private data class ParsedTime(val hour: Int, val min: Int, val sec: Int, val ns: Int, val offsetSec: Int)

/**
 * Result of parsing a string into its component parts. We only deal with parsing, conversion to [Instant]
 * and dealing with TZ offsets will be handled by the underlying implementation
 */
internal data class ParsedDatetime(
    // 4 digit year
    val year: Int,
    // month Jan-Dec as 1-12
    val month: Int,
    // 1-31
    val day: Int,
    // 0-23
    val hour: Int,
    // 0-59
    val min: Int,
    // 0-60
    val sec: Int,
    // 0-999_999_999
    val ns: Int,
    // offset from UTC in seconds
    val offsetSec: Int = 0
)

// YYYY
private fun dateYear(): Parser<Int> = takeNDigits(4)
// MM
private fun dateMonth(): Parser<Int> = nDigitsInRange(2, 1..12)
// DD (set minDigits to 1 to allow single digit D)
private fun dateDay(minDigits: Int = 2): Parser<Int> = mnDigitsInRange(minDigits, 2, 1..31)

// YYYY[-]MM[-]DD
private fun dateYmd(): Parser<ParsedDate> = { str, pos ->
    val (pos0, year) = dateYear()(str, pos)
    val (pos1, _) = optional(char('-'))(str, pos0)
    val (pos2, month) = dateMonth()(str, pos1)
    val (pos3, _) = optional(char('-'))(str, pos2)
    val (pos4, day) = dateDay()(str, pos3)

    ParseResult(pos4, ParsedDate(year, month, day))
}

// allow for alternatives to be added. currently we don't parse week dates or ordinal dates (neither does CRT)
// see: https://github.com/awslabs/aws-c-common/blob/87540e496a7d1b5c6c778b4cee42cd865c69f7dd/source/date_time.c
private fun date(): Parser<ParsedDate> = dateYmd()

// HH
private fun timeHour(): Parser<Int> = nDigitsInRange(2, 0..24)
// MM
private fun timeMin(): Parser<Int> = nDigitsInRange(2, 0..59)
// SS (note 60 is valid and denotes a leap second)
private fun timeSec(): Parser<Int> = nDigitsInRange(2, 0..60)
// .sss
private fun timeNanos(): Parser<Int> = fraction(1, 9, 9)

// +|-
// result is returned as a positive or negative integer of magnitude 1
private fun signValue(): Parser<Int> = { str, pos ->
    val (pos0, s) = map(alt(char('+'), char('-'))) { chr ->
        when (chr) {
            '-' -> -1
            else -> 1
        }
    }(str, pos)

    ParseResult(pos0, s)
}

// Get the TZ offset in (+-) seconds
// +|-hh:mm
// +|-hhmm
// +|-hh
private fun tzOffsetHoursMins(): Parser<Int> = { str, pos ->
    val (pos0, sign) = signValue()(str, pos)
    val (pos1, hours) = timeHour()(str, pos0)
    val (pos2, _) = optional(char(':'))(str, pos1)

    val (pos3, min) = if (pos2 < str.length) {
        timeMin()(str, pos2)
    } else {
        ParseResult(pos2, 0)
    }

    val offsetSec: Int = sign * (hours * 3600 + min * 60)

    ParseResult(pos3, offsetSec)
}

// Z|z
private fun tzUtc(): Parser<Int> = map(oneOf("Zz")) { 0 }

private fun tzOffsetSecIso8601(): Parser<Int> = { str, pos ->
    try {
        alt(tzUtc(), tzOffsetHoursMins())(str, pos)
    } catch (_: ParseException) {
        // improve the error message
        throw ParseException(str, "invalid timezone offset; expected `Z|z` or `(+-)HH:MM`", pos)
    }
}

// HH:MM:[SS][.(s*)][(Z|z|+...|-...)]
private fun iso8601Time(): Parser<ParsedTime> = { str, pos ->
    val (pos0, hour) = timeHour()(str, pos)
    val (pos1, _) = optional(char(':'))(str, pos0)
    val (pos2, min) = timeMin()(str, pos1)

    val (pos3, _) = optional(char(':'))(str, pos2)
    val (pos4, sec) = timeSec()(str, pos3)
    val (pos5, ns) = optionalOr(preceded(oneOf(".,"), timeNanos()), 0)(str, pos4)
    val (pos6, offsetSec) = tzOffsetSecIso8601()(str, pos5)

    // ParseResult(pos6, ParsedTime(hour, min, sec ?: 0, ns ?: 0, offsetSec ?: 0))
    ParseResult(pos6, ParsedTime(hour, min, sec, ns, offsetSec))
}

/**
 * Parse a full ISO-8601 (including RFC3339) datetime into it's component parts
 *
 * Notes:
 * - If there is no timestamp (e.g. YYYY-MM-DD) then the timestamp is zeroed and assumed UTC
 * - The `T` separator between date and time is assumed. Other formats, e.g. using a space instead,
 *   are not supported because they have to be mutually agreed upon.
 */
internal fun parseIso8601(input: String): ParsedDatetime {
    val (pos0, date) = date()(input, 0)

    // check for end of input OR parse timestamp, using optional would swallow real errors with invalid timestamps
    val ts = if (pos0 == input.length) {
        // basic date, no time portion found (raw date e.g. YYYY-MM-DD); assumed UTC
        ParsedTime(0, 0, 0, 0, 0)
    } else {
        // full datetime assumed
        val (_, time) = preceded(oneOf("Tt"), iso8601Time())(input, pos0)
        time
    }

    return ParsedDatetime(date.year, date.month, date.day, ts.hour, ts.min, ts.sec, ts.ns, ts.offsetSec)
}

/**
 * Parse an epoch timestamp (with or without fractional seconds) into an instant
 */
internal fun parseEpoch(input: String): Instant {
    val (pos0, secs) = takeMNDigitsLong(1, 19)(input, 0)
    return if (pos0 == input.length) {
        Instant.fromEpochSeconds(secs, 0)
    } else {
        val (_, ns) = preceded(char('.'), fraction(1, 9, 9))(input, pos0)
        Instant.fromEpochSeconds(secs, ns)
    }
}

private fun dayName(): Parser<String> = alt(
    tag("Mon"),
    tag("Tue"),
    tag("Wed"),
    tag("Thu"),
    tag("Fri"),
    tag("Sat"),
    tag("Sun"))

/**
 * Match literal whitespace
 */
private fun sp(input: String, pos: Int): ParseResult<Char> = char(' ')(input, pos)

private fun dateMonthName(): Parser<Int> = { str, pos ->
    precond(str, pos, 3)
    val name = str.substring(pos, pos + 3)
    val monthNum = when (name) {
        "Jan" -> 1
        "Feb" -> 2
        "Mar" -> 3
        "Apr" -> 4
        "May" -> 5
        "Jun" -> 6
        "Jul" -> 7
        "Aug" -> 8
        "Sep" -> 9
        "Oct" -> 10
        "Nov" -> 11
        "Dec" -> 12
        else -> throw ParseException(str, "invalid month `$name`", pos)
    }

    ParseResult(pos + 3, monthNum)
}

private fun tzOffsetSecRfc5322(): Parser<Int> = { str, pos ->
    try {
        val utcOffsets = map(alt(tag("GMT"), tag("UTC"), tag("UT"), tag("Z"))) { 0 }
        alt(utcOffsets, tzOffsetHoursMins())(str, pos)
    } catch (_: ParseException) {
        // improve the error message
        throw ParseException(str, "invalid timezone offset; expected `GMT` or `(+-)HHMM`", pos)
    }
}

// parse RFC-5322 timestamp
// HH:MM[:SS] GMT
private fun rfc5322Time(): Parser<ParsedTime> = { str, pos ->
    val (pos0, hour) = timeHour()(str, pos)
    val (pos1, min) = preceded(char(':'), timeMin())(str, pos0)

    val (pos2, sec) = if (pos1 < str.length && str[pos1] == ':') {
        preceded(char(':'), timeSec())(str, pos1)
    } else {
        ParseResult(pos1, 0)
    }
    val (pos3, offsetSec) = preceded(::sp, tzOffsetSecRfc5322())(str, pos2)
    ParseResult(pos3, ParsedTime(hour, min, sec, 0, offsetSec))
}

/**
 * Parse an HTTP-date as defined by the IMF-fixdate production in
 * RFC 7231#section-7.1.1.1 (for example, Tue, 29 Apr 2014 18:30:38 GMT).
 *
 * Which is is the fixed-length and single-zone subset of the date and time
 * specification used by the Internet Message Format RFC5322 (which supersedes RFC822)
 *
 * See:
 * - https://tools.ietf.org/html/rfc7231.html#section-7.1.1.1
 * - https://tools.ietf.org/html/rfc5322
 */
internal fun parseRfc5322(input: String): ParsedDatetime {

    // dow is optional, if first character is digit skip it
    // otherwise consume it and advance
    val (pos0, _) = if (input.isNotBlank() && !isDigit(input[0])) {
        // must be e.g. `Mon, ` with the space not optional when dow is present
        map(dayName()
            .then(char(','))
            .then(::sp)) { null }(input, 0)
    } else {
        ParseResult(0, null)
    }

    // rfc5322 allows single digit days
    val (pos1, day) = dateDay(minDigits = 1)(input, pos0)
    val (pos2, month) = preceded(::sp, dateMonthName())(input, pos1)
    val (pos3, year) = preceded(::sp, dateYear())(input, pos2)
    val (_, ts) = preceded(::sp, rfc5322Time())(input, pos3)

    // Sun, 06 Nov 1994 08:49:37 GMT
    return ParsedDatetime(year, month, day, ts.hour, ts.min, ts.sec, 0, ts.offsetSec)
}
