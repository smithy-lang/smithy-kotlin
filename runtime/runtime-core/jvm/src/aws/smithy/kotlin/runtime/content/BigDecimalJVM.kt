/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

import java.math.BigDecimal as JvmBigDecimal

public actual class BigDecimal private constructor(private val delegate: JvmBigDecimal) :
    Number(),
    Comparable<BigDecimal> {

    private companion object {
        /**
         * Returns a new or existing [BigDecimal] wrapper for the given delegate [value]
         * @param value The delegate value to wrap
         * @param left A candidate wrapper which may already contain [value]
         * @param right A candidate wrapper which may already contain [value]
         */
        fun coalesceOrCreate(value: JvmBigDecimal, left: BigDecimal, right: BigDecimal): BigDecimal = when (value) {
            left.delegate -> left
            right.delegate -> right
            else -> BigDecimal(value)
        }
    }

    public actual constructor(value: String) : this(JvmBigDecimal(value))
    public actual constructor(mantissa: BigInteger, exponent: Int) : this(JvmBigDecimal(mantissa.delegate, exponent))

    public actual fun toPlainString(): String = delegate.toPlainString()
    public actual override fun toString(): String = delegate.toString()
    public actual override fun toByte(): Byte = delegate.toByte()
    public actual override fun toDouble(): Double = delegate.toDouble()
    public actual override fun toFloat(): Float = delegate.toFloat()
    public actual override fun toInt(): Int = delegate.toInt()
    public actual override fun toLong(): Long = delegate.toLong()
    public actual override fun toShort(): Short = delegate.toShort()

    public actual override fun equals(other: Any?): Boolean = other is BigDecimal && delegate == other.delegate
    public actual override fun hashCode(): Int = 31 + delegate.hashCode()

    public actual val mantissa: BigInteger
        get() = BigInteger(delegate.unscaledValue())

    public actual val exponent: Int
        get() = delegate.scale()

    public actual operator fun plus(other: BigDecimal): BigDecimal =
        coalesceOrCreate(delegate + other.delegate, this, other)

    public actual operator fun minus(other: BigDecimal): BigDecimal =
        coalesceOrCreate(delegate - other.delegate, this, other)

    actual override fun compareTo(other: BigDecimal): Int = delegate.compareTo(other.delegate)
}
