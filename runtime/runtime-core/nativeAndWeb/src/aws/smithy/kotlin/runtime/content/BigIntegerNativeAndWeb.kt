/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

import com.ionspin.kotlin.bignum.integer.util.fromTwosComplementByteArray
import com.ionspin.kotlin.bignum.integer.util.toTwosComplementByteArray
import com.ionspin.kotlin.bignum.integer.BigInteger as IonSpinBigInteger

public actual class BigInteger internal constructor(internal val delegate: IonSpinBigInteger) :
    Number(),
    Comparable<BigInteger> {

    private companion object {
        /**
         * Returns a new or existing [BigInteger] wrapper for the given delegate [value]
         * @param value The delegate value to wrap
         * @param left A candidate wrapper which may already contain [value]
         * @param right A candidate wrapper which may already contain [value]
         */
        fun coalesceOrCreate(value: IonSpinBigInteger, left: BigInteger, right: BigInteger): BigInteger = when (value) {
            left.delegate -> left
            right.delegate -> right
            else -> BigInteger(value)
        }
    }

    public actual constructor(value: String) : this(IonSpinBigInteger.parseString(value, 10))
    public actual constructor(bytes: ByteArray) : this(IonSpinBigInteger.fromTwosComplementByteArray(bytes))

    public actual override fun toByte(): Byte = delegate.byteValue(exactRequired = false)
    public actual override fun toDouble(): Double = delegate.doubleValue(exactRequired = false)
    public actual override fun toFloat(): Float = delegate.floatValue(exactRequired = false)
    public actual override fun toInt(): Int = delegate.intValue(exactRequired = false)
    public actual override fun toLong(): Long = delegate.longValue(exactRequired = false)
    public actual override fun toShort(): Short = delegate.shortValue(exactRequired = false)

    public actual operator fun plus(other: BigInteger): BigInteger = coalesceOrCreate(delegate + other.delegate, this, other)

    public actual operator fun minus(other: BigInteger): BigInteger = coalesceOrCreate(delegate - other.delegate, this, other)

    public actual fun toByteArray(): ByteArray = delegate.toTwosComplementByteArray()
    public actual override fun compareTo(other: BigInteger): Int = delegate.compare(other.delegate)
    public actual override fun equals(other: Any?): Boolean = other is BigInteger && other.delegate == delegate
    public actual override fun hashCode(): Int = 17 + delegate.hashCode()
    public actual override fun toString(): String = toString(10)
    public actual fun toString(radix: Int): String = delegate.toString(radix)
}
