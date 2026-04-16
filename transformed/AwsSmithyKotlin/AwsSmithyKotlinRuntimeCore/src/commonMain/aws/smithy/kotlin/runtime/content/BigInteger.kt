/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

/**
 * An arbitrarily large signed integer
 * @param value the string representation of this large integer
 */
public expect class BigInteger(value: String) :
    Number,
    Comparable<BigInteger> {

    /**
     * Create an instance of [BigInteger] from a [ByteArray]
     * @param bytes ByteArray representing the large integer
     */
    public constructor(bytes: ByteArray)

    /**
     * Converts this value to a [Byte], which may involve rounding or truncation
     */
    override fun toByte(): Byte

    /**
     * Converts this value to a [Long], which may involve rounding or truncation
     */
    override fun toLong(): Long

    /**
     * Converts this value to a [Short], which may involve rounding or truncation
     */
    override fun toShort(): Short

    /**
     * Converts this value to an [Int], which may involve rounding or truncation
     */
    override fun toInt(): Int

    /**
     * Converts this value to a [Float], which may involve rounding or truncation
     */
    override fun toFloat(): Float

    /**
     * Converts this value to a [Double], which may involve rounding or truncation
     */
    override fun toDouble(): Double

    /**
     * Returns the decimal (i.e., radix-10) string representation of this value
     */
    override fun toString(): String

    /**
     * Returns a string representation of this value in the given radix
     * @param radix The [numerical base](https://en.wikipedia.org/wiki/Radix) in which to represent the value
     */
    public fun toString(radix: Int = 10): String

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
    public operator fun plus(other: BigInteger): BigInteger

    /**
     * Returns the difference of this value and the given value
     * @param other The value to subtract (i.e., the subtrahend)
     */
    public operator fun minus(other: BigInteger): BigInteger

    /**
     * Returns the [two's complement](https://en.wikipedia.org/wiki/Two%27s_complement) binary representation of this
     * value
     */
    public fun toByteArray(): ByteArray

    /**
     * Compare this value to the given value for in/equality
     * @param other The value to compare against
     */
    public override operator fun compareTo(other: BigInteger): Int
}
