/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

import java.math.BigInteger as JvmBigInteger

public actual class BigInteger internal constructor(internal val delegate: JvmBigInteger) :
    Number(),
    Comparable<BigInteger> {

    private companion object {
        /**
         * Returns a new or existing [BigInteger] wrapper for the given delegate [value]
         * @param value The delegate value to wrap
         * @param left A candidate wrapper which may already contain [value]
         * @param right A candidate wrapper which may already contain [value]
         */
        fun coalesceOrCreate(value: JvmBigInteger, left: BigInteger, right: BigInteger): BigInteger = when (value) {
            left.delegate -> left
            right.delegate -> right
            else -> BigInteger(value)
        }
    }

    public actual constructor(value: String) : this(JvmBigInteger(value))
    public actual constructor(bytes: ByteArray) : this(JvmBigInteger(bytes))

    public actual override fun toByte(): Byte = delegate.toByte()
    public actual override fun toLong(): Long = delegate.toLong()
    public actual override fun toShort(): Short = delegate.toShort()
    public actual override fun toInt(): Int = delegate.toInt()
    public actual override fun toFloat(): Float = delegate.toFloat()
    public actual override fun toDouble(): Double = delegate.toDouble()
    public actual override fun toString(): String = toString(10)
    public actual fun toString(radix: Int): String = delegate.toString(radix)
    public val value: String = delegate.toString()

    public actual override fun hashCode(): Int = 17 + delegate.hashCode()
    public actual override fun equals(other: Any?): Boolean = other is BigInteger && delegate == other.delegate

    public actual operator fun plus(other: BigInteger): BigInteger = coalesceOrCreate(delegate + other.delegate, this, other)

    public actual operator fun minus(other: BigInteger): BigInteger = coalesceOrCreate(delegate - other.delegate, this, other)

    public actual override operator fun compareTo(other: BigInteger): Int = delegate.compareTo(other.delegate)
    public actual fun toByteArray(): ByteArray = delegate.toByteArray()
}
