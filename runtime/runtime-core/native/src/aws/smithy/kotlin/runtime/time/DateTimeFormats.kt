/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.alternativeParsing
import kotlinx.datetime.format.char
import kotlinx.datetime.format.optional

/**
 * [DateTimeFormat<DateTimeComponents>] for use with [kotlinx.datetime.Instant]
 */
internal object DateTimeFormats {

    /**
     * ISO8601, full precision. Corresponds to [TimestampFormat.ISO_8601_FULL]. Truncate to microseconds for [TimestampFormat.ISO_8601].
     * e.g. "2020-11-05T19:22:37+00:00"
     */
    val ISO_8601 = DateTimeComponents.Format {
        // Two possible date formats: YYYY-MM-DD or YYYYMMDD
        alternativeParsing({
            date(
                LocalDate.Format {
                    year()
                    monthNumber()
                    dayOfMonth()
                },
            )
        }) {
            date(
                LocalDate.Format {
                    year()
                    char('-')
                    monthNumber()
                    char('-')
                    dayOfMonth()
                },
            )
        }

        char('T')

        // Two possible time formats: HH:MM:SS or HHMMSS
        alternativeParsing({
            hour()
            minute()
            second()
        }) {
            hour()
            char(':')
            minute()
            char(':')
            second()
        }

        // Fractional seconds
        optional {
            char('.')
            secondFraction(1, 9)
        }

        // Offsets
        alternativeParsing({
            offsetHours()
        }) {
            offset(UtcOffset.Formats.ISO)
        }
    }

    /**
     * ISO8601 condensed. Corresponds to [TimestampFormat.ISO_8601_CONDENSED].
     */
    val ISO_8601_CONDENSED = DateTimeComponents.Format {
        year()
        monthNumber()
        dayOfMonth()

        char('T')
        hour()
        minute()
        second()
        char('Z')
    }

    /**
     * ISO8601 condensed, date only. Corresponds to [TimestampFormat.ISO_8601_CONDENSED_DATE]
     */
    val ISO_8601_CONDENSED_DATE = DateTimeComponents.Format {
        year()
        monthNumber()
        dayOfMonth()
    }

    /**
     * [RFC-5322/2822/822 IMF timestamp](https://tools.ietf.org/html/rfc5322). Corresponds to [TimestampFormat.RFC_5322].
     * e.g. "Thu, 05 Nov 2020 19:22:37 +0000"
     */
    val RFC_5322 = DateTimeComponents.Format {
        dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
        chars(", ")

        dayOfMonth()
        char(' ')
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char(' ')
        year()
        char(' ')

        hour()
        char(':')
        minute()
        char(':')
        second()
        char(' ')

        optional("GMT") {
            offset(UtcOffset.Formats.FOUR_DIGITS)
        }
    }
}
