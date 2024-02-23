/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.TimestampFormat

@InternalApi
public inline fun <T> String.parse(transform: (String) -> T): Result<T> = runCatching { transform(this) }

@InternalApi
public fun String.parseBoolean(): Result<Boolean> = parse(String::toBoolean)

@InternalApi
public fun String.parseInt(): Result<Int> = parse(String::toInt)

@InternalApi
public fun String.parseShort(): Result<Short> = parse(String::toShort)

@InternalApi
public fun String.parseLong(): Result<Long> = parse(String::toLong)

@InternalApi
public fun String.parseFloat(): Result<Float> = parse(String::toFloat)

@InternalApi
public fun String.parseDouble(): Result<Double> = parse(String::toDouble)

@InternalApi
public fun String.parseByte(): Result<Byte> = parse { it.toInt().toByte() }

public fun String.parseBigInteger(): Result<BigInteger> = parse(::BigInteger)

@InternalApi
public fun String.parseBigDecimal(): Result<BigDecimal> = parse(::BigDecimal)

private fun String.toTimestamp(fmt: TimestampFormat): Instant = when (fmt) {
    TimestampFormat.ISO_8601_CONDENSED,
    TimestampFormat.ISO_8601_CONDENSED_DATE,
    TimestampFormat.ISO_8601,
    -> Instant.fromIso8601(this)

    TimestampFormat.RFC_5322 -> Instant.fromRfc5322(this)
    TimestampFormat.EPOCH_SECONDS -> Instant.fromEpochSeconds(this)
}

@InternalApi
public fun String.parseTimestamp(fmt: TimestampFormat): Result<Instant> = parse { it.toTimestamp(fmt) }

@InternalApi
public inline fun <T> Result<String>.parse(transform: (String) -> T): Result<T> = mapCatching(transform)

@InternalApi
public fun Result<String>.parseBoolean(): Result<Boolean> = parse(String::toBoolean)

@InternalApi
public fun Result<String>.parseInt(): Result<Int> = parse(String::toInt)

@InternalApi
public fun Result<String>.parseShort(): Result<Short> = parse(String::toShort)

@InternalApi
public fun Result<String>.parseLong(): Result<Long> = parse(String::toLong)

@InternalApi
public fun Result<String>.parseFloat(): Result<Float> = parse(String::toFloat)

@InternalApi
public fun Result<String>.parseDouble(): Result<Double> = parse(String::toDouble)

@InternalApi
public fun Result<String>.parseByte(): Result<Byte> = parse { it.toInt().toByte() }

@InternalApi
public fun Result<String>.parseBigInteger(): Result<BigInteger> = parse(::BigInteger)

@InternalApi
public fun Result<String>.parseBigDecimal(): Result<BigDecimal> = parse(::BigDecimal)

@InternalApi
public fun Result<String>.parseTimestamp(fmt: TimestampFormat): Result<Instant> = parse { it.toTimestamp(fmt) }
