/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

@file:JsModule("@js-joda/core")
@file:JsNonModule
package software.aws.clientrt.time.externals

open external class Instant: TemporalAccessor {
    open fun compareTo(otherInstant: Instant): Number
    open fun epochSecond(): Number
    open fun nano(): Number

    companion object {
        fun now(): Instant
        fun ofEpochSecond(epochSecond: Number, nanoAdjustment: Number): Instant
    }
}

open external class LocalDateTime {
    open fun atOffset(offset: ZoneOffset): OffsetDateTime
    open fun plusDays(days: Number): LocalDateTime

    companion object {
        fun of(year: Number, month: Number, dayOfMonth: Number, hour: Number, minute: Number, second: Number, nanoSecond: Number): LocalDateTime
    }
}

open external class ZoneOffset {
    companion object {
        fun ofTotalSeconds(totalSeconds: Number): ZoneOffset
    }
}

open external class OffsetDateTime {
    open fun toInstant(): Instant
}

open external class TemporalAccessor

open external class DateTimeFormatter {
    open fun format(temporal: TemporalAccessor): String
    companion object {
        var ISO_INSTANT: DateTimeFormatter
    }
}

open external class ZonedDateTime: TemporalAccessor {
    open fun dayOfMonth(): Number
    open fun dayOfWeek(): DayOfWeek
    open fun hour(): Number
    open fun minute(): Number
    open fun monthValue(): Number
    open fun second(): Number
    open fun year(): Number

    companion object {
        fun ofInstant(instant: Instant, zone: ZoneId): ZonedDateTime
    }
}

open external class DayOfWeek : TemporalAccessor {
    open fun value(): Number
}

open external class ZoneId {
    companion object {
        var UTC: ZoneId
    }
}