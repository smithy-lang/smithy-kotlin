/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.decimal.BigDecimal as IonSpinBigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger as IonSpinBigInteger

/**
 * Rounding mode paired with every scale handed to ionspin. ionspin requires a non-NONE mode whenever a scale is set,
 * but since we only ever apply a scale at least as large as the value's actual fractional digits, no rounding occurs.
 * This matches the mode ionspin itself uses in [IonSpinBigDecimal.scale] for values without a decimal mode.
 */
private val SCALE_ROUNDING_MODE = RoundingMode.ROUND_HALF_AWAY_FROM_ZERO

/**
 * The number of digits to the right of the decimal point (i.e., `java.math.BigDecimal.scale()`), clamped to zero
 * because ionspin doesn't support negative scales (which don't affect plain-string rendering anyway).
 */
private val IonSpinBigDecimal.nonNegativeScale: Long
    get() = scale.coerceAtLeast(0)

/**
 * Returns an equivalent value carrying exactly [scale] fractional digits (or no scale if [scale] is not positive).
 *
 * ionspin's factory methods which accept a [DecimalMode] (`fromBigDecimal`, `fromBigIntegerWithExponent`) also round
 * the significand to `decimalPrecision + scale` digits, which truncates values when precision is unlimited (0). The
 * value is therefore rebuilt without a mode and then given a scale via [IonSpinBigDecimal.scale], which only pads.
 */
private fun IonSpinBigDecimal.withScale(scale: Long): IonSpinBigDecimal {
    val unscaled = IonSpinBigDecimal.fromBigIntegerWithExponent(significand, exponent)
    return if (scale > 0) unscaled.scale(scale) else unscaled
}

/**
 * Computes the scale of a decimal string the same way `java.math.BigDecimal(String)` does: the number of fractional
 * digits minus the value of any exponent. Returns 0 for malformed exponents so the parser reports the error.
 */
private fun parseScale(value: String): Long {
    val exponentIndex = value.indexOfFirst { it == 'e' || it == 'E' }
    val significandPart = if (exponentIndex == -1) value else value.substring(0, exponentIndex)
    val dotIndex = significandPart.indexOf('.')
    val fractionDigits = if (dotIndex == -1) 0 else significandPart.length - dotIndex - 1
    val exponent = if (exponentIndex == -1) 0 else value.substring(exponentIndex + 1).toLongOrNull() ?: return 0
    return fractionDigits - exponent
}

private fun parseWithScale(value: String): IonSpinBigDecimal {
    val scale = parseScale(value)
    // Bound the scale before parsing because ionspin materializes it as trailing zeroes in the significand
    assertExponent(scale)
    val mode = if (scale > 0) DecimalMode(0, SCALE_ROUNDING_MODE, scale) else null
    return IonSpinBigDecimal.parseStringWithMode(value, mode)
}

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
        fun coalesceOrCreate(value: IonSpinBigDecimal, left: BigDecimal, right: BigDecimal): BigDecimal = when {
            value.isSameRepresentation(left.delegate) -> left
            value.isSameRepresentation(right.delegate) -> right
            else -> BigDecimal(value)
        }

        // ionspin's equals is numeric only (e.g., 1.5 == 1.50); reuse also requires a matching scale, like JVM equals
        private fun IonSpinBigDecimal.isSameRepresentation(other: IonSpinBigDecimal) = this == other && nonNegativeScale == other.nonNegativeScale

        /**
         * Applies [op] with exact (unlimited precision, unscaled) arithmetic, then restores the larger of the operands'
         * scales as `java.math.BigDecimal` does for addition and subtraction. ionspin's own scaled arithmetic can't be
         * relied upon here: it drops the scale when only one operand has one or when either operand is zero, and
         * `subtract` consults the receiver's scale rather than the supplied mode.
         */
        fun scaledArithmetic(
            left: BigDecimal,
            right: BigDecimal,
            op: (IonSpinBigDecimal, IonSpinBigDecimal) -> IonSpinBigDecimal,
        ): BigDecimal {
            val resultScale = maxOf(left.delegate.nonNegativeScale, right.delegate.nonNegativeScale)
            val exact = op(left.delegate.withScale(0), right.delegate.withScale(0))
            return coalesceOrCreate(exact.withScale(resultScale), left, right)
        }
    }

    public actual constructor(value: String) : this(parseWithScale(value))

    public actual constructor(mantissa: BigInteger, exponent: Int) : this(
        IonSpinBigDecimal
            .fromBigIntegerWithExponent(mantissa.delegate, exponent.toLong())
            // Same scale derivation as the JVM actual: digits to the right of the decimal point
            .withScale(mantissa.delegate.abs().toString().length - 1L - exponent),
    )

    private val normalized by lazy {
        if (delegate.significand.isZero()) {
            delegate
        } else {
            val sig = delegate.significand
            var stripped = sig
            while (stripped.mod(IonSpinBigInteger.TEN) == IonSpinBigInteger.ZERO && !stripped.isZero()) {
                stripped = stripped.div(IonSpinBigInteger.TEN)
            }
            IonSpinBigDecimal.fromBigIntegerWithExponent(stripped, delegate.exponent)
        }
    }

    init {
        assertExponent(exponent)
    }

    actual override fun toByte(): Byte = delegate.byteValue(exactRequired = false)
    actual override fun toDouble(): Double = delegate.doubleValue(exactRequired = false)
    actual override fun toFloat(): Float = delegate.floatValue(exactRequired = false)
    actual override fun toInt(): Int = delegate.intValue(exactRequired = false)
    actual override fun toLong(): Long = delegate.longValue(exactRequired = false)
    actual override fun toShort(): Short = delegate.shortValue(exactRequired = false)

    public actual val mantissa: BigInteger
        get() = BigInteger(normalized.significand)

    public actual val exponent: Int
        get() = normalized.exponent.toInt()

    actual override fun compareTo(other: BigDecimal): Int = normalized.compare(other.normalized)
    public actual override fun equals(other: Any?): Boolean = other is BigDecimal && (delegate.compareTo(other.delegate) == 0)
    actual override fun hashCode(): Int = 31 * normalized.hashCode()
    public actual fun toPlainString(): String = delegate.toPlainString()
    actual override fun toString(): String = delegate.toString()

    public actual operator fun plus(other: BigDecimal): BigDecimal = scaledArithmetic(this, other) { a, b -> a.add(b, DecimalMode.DEFAULT) }

    public actual operator fun minus(other: BigDecimal): BigDecimal = scaledArithmetic(this, other) { a, b -> a.subtract(b, DecimalMode.DEFAULT) }
}
