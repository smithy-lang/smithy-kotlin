/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

import com.ionspin.kotlin.bignum.decimal.BigDecimal as IonSpinBigDecimal

public actual class BigDecimal private constructor(private val delegate: IonSpinBigDecimal) :
    Number(),
    Comparable<BigDecimal> {

    private companion object {
        /**
         * Returns a new or existing [BigDecimal] wrapper for the given delegate [value]
         * @param value The delegate value to wrap
         * @param left A candidate wrapper which may already contain [value]
         * @param right A candidate wrapper which may already contain [value]
         */
        fun coalesceOrCreate(value: IonSpinBigDecimal, left: BigDecimal, right: BigDecimal): BigDecimal = when (value) {
            left.delegate -> left
            right.delegate -> right
            else -> BigDecimal(value)
        }
    }

    public actual constructor(value: String) : this(IonSpinBigDecimal.parseString(value, 10))

    public actual constructor(mantissa: BigInteger, exponent: Int) :
        this(IonSpinBigDecimal.fromBigIntegerWithExponent(mantissa.delegate, exponent.toLong()))

    actual override fun toByte(): Byte = delegate.byteValue(exactRequired = false)
    actual override fun toDouble(): Double = delegate.doubleValue(exactRequired = false)
    actual override fun toFloat(): Float = delegate.floatValue(exactRequired = false)
    actual override fun toInt(): Int = delegate.intValue(exactRequired = false)
    actual override fun toLong(): Long = delegate.longValue(exactRequired = false)
    actual override fun toShort(): Short = delegate.shortValue(exactRequired = false)

    public actual val mantissa: BigInteger
        get() = BigInteger(delegate.significand)

    public actual val exponent: Int
        get() = delegate.exponent.toInt()

    actual override fun compareTo(other: BigDecimal): Int = delegate.compare(other.delegate)
    actual override fun equals(other: Any?): Boolean = other is BigDecimal && other.delegate == delegate
    actual override fun hashCode(): Int = 31 + delegate.hashCode()
    public actual fun toPlainString(): String = delegate.toPlainString()
    actual override fun toString(): String = delegate.toString()

    public actual operator fun plus(other: BigDecimal): BigDecimal = coalesceOrCreate(delegate + other.delegate, this, other)

    public actual operator fun minus(other: BigDecimal): BigDecimal = coalesceOrCreate(delegate - other.delegate, this, other)
}
