/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

import com.ionspin.kotlin.bignum.integer.util.fromTwosComplementByteArray
import com.ionspin.kotlin.bignum.integer.util.toTwosComplementByteArray
import com.ionspin.kotlin.bignum.integer.BigInteger as IonSpinBigInteger

public actual class BigInteger private constructor(private val delegate: IonSpinBigInteger) :
    Number(),
    Comparable<BigInteger> {

    private companion object {
        fun coalesceOrCreate(value: IonSpinBigInteger, left: BigInteger, right: BigInteger): BigInteger = when (value) {
            left.delegate -> left
            right.delegate -> right
            else -> BigInteger(value)
        }
    }

    public actual constructor(value: String) : this(IonSpinBigInteger.parseString(value, 10))
    public actual constructor(bytes: ByteArray) : this(IonSpinBigInteger.fromTwosComplementByteArray(bytes))

    actual override fun toByte(): Byte = delegate.byteValue(exactRequired = false)
    actual override fun toDouble(): Double = delegate.doubleValue(exactRequired = false)
    actual override fun toFloat(): Float = delegate.floatValue(exactRequired = false)
    actual override fun toInt(): Int = delegate.intValue(exactRequired = false)
    actual override fun toLong(): Long = delegate.longValue(exactRequired = false)
    actual override fun toShort(): Short = delegate.shortValue(exactRequired = false)

    public actual operator fun plus(other: BigInteger): BigInteger =
        coalesceOrCreate(delegate + other.delegate, this, other)

    public actual operator fun minus(other: BigInteger): BigInteger =
        coalesceOrCreate(delegate - other.delegate, this, other)

    public actual fun toByteArray(): ByteArray = delegate.toTwosComplementByteArray()
    actual override fun compareTo(other: BigInteger): Int = delegate.compare(other.delegate)
}
