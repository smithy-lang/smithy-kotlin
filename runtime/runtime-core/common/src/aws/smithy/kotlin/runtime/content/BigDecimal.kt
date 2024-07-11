/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

/**
 * A floating point decimal number with arbitrary precision.
 * @param value the [String] representation of this decimal number
 */
public expect class BigDecimal(value: String) : Number, Comparable<BigDecimal> {
    /**
     * Create an instance of [BigDecimal] from a mantissa and exponent.
     * @param mantissa a [BigInteger] representing the mantissa of this big decimal
     * @param exponent an [Int] representing the exponent of this big decimal
     */
    public constructor(mantissa: BigInteger, exponent: Int)

    /**
     * The mantissa of this decimal number
     */
    public val mantissa: BigInteger

    /**
     * The exponent of this decimal number.
     * If zero or positive, this represents the number of digits to the right of the decimal point.
     * If negative, the mantissa is multiplied by ten to the power of the negation of the scale.
     */
    public val exponent: Int

    override fun toByte(): Byte
    override fun toDouble(): Double
    override fun toFloat(): Float
    override fun toShort(): Short
    override fun toInt(): Int
    override fun toLong(): Long
    public fun toPlainString(): String
    override fun equals(other: Any?): Boolean
    public override operator fun compareTo(other: BigDecimal): Int
}