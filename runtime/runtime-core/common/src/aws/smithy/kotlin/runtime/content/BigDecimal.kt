/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

/**
 * A floating point decimal number with arbitrary precision.
 * @param value the [String] representation of this decimal number
 */
public expect class BigDecimal(value: String) :
    Number,
    Comparable<BigDecimal> {

    /**
     * Create an instance of [BigDecimal] from a mantissa and exponent.
     * @param mantissa a [BigInteger] representing the [significant digits](https://en.wikipedia.org/wiki/Significand)
     * of this decimal value
     * @param exponent an [Int] representing the exponent of this decimal value
     */
    public constructor(mantissa: BigInteger, exponent: Int)

    /**
     * The [significant digits](https://en.wikipedia.org/wiki/Significand) of this decimal value
     */
    public val mantissa: BigInteger

    /**
     * The exponent of this decimal number. If zero or positive, this represents the number of digits to the right of
     * the decimal point. If negative, the [mantissa] is multiplied by ten to the power of the negation of the scale.
     */
    public val exponent: Int

    /**
     * Converts this value to a [Byte], which may involve rounding or truncation
     */
    override fun toByte(): Byte

    /**
     * Converts this value to a [Double], which may involve rounding or truncation
     */
    override fun toDouble(): Double

    /**
     * Converts this value to a [Float], which may involve rounding or truncation
     */
    override fun toFloat(): Float

    /**
     * Converts this value to a [Short], which may involve rounding or truncation
     */
    override fun toShort(): Short

    /**
     * Converts this value to an [Int], which may involve rounding or truncation
     */
    override fun toInt(): Int

    /**
     * Converts this value to a [Long], which may involve rounding or truncation
     */
    override fun toLong(): Long

    /**
     * Returns the decimal (i.e., radix-10) string representation of this value in long-form (i.e., _not_ scientific)
     * notation
     */
    public fun toPlainString(): String

    /**
     * Returns the decimal (i.e., radix-10) string representation of this value using scientific notation if an exponent
     * is needed
     */
    override fun toString(): String

    /**
     * Returns a hash code for this value
     */
    override fun hashCode(): Int

    /**
     * Checks if this value is equal to the given object
     * @param other The other value to compare against
     */
    override fun equals(other: Any?): Boolean

    /**
     * Returns the sum of this value and the given value
     * @param other The other value to add (i.e., the addend)
     */
    public operator fun plus(other: BigDecimal): BigDecimal

    /**
     * Returns the difference of this value and the given value
     * @param other The value to subtract (i.e., the subtrahend)
     */
    public operator fun minus(other: BigDecimal): BigDecimal

    /**
     * Compare this value to the given value for in/equality
     * @param other The value to compare against
     */
    public override operator fun compareTo(other: BigDecimal): Int
}
