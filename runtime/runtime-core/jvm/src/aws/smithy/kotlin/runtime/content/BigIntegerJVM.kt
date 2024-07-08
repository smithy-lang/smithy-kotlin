/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

public actual class BigInteger actual constructor(public val value: String) : Number() {
    private val delegate = java.math.BigInteger(value)

    public actual constructor(bytes: ByteArray) : this(java.math.BigInteger(bytes).toString())

    public actual override fun toByte(): Byte = delegate.toByte()
    public actual override fun toLong(): Long = delegate.toLong()
    public actual override fun toShort(): Short = delegate.toShort()
    public actual override fun toInt(): Int = delegate.toInt()
    public actual override fun toFloat(): Float = delegate.toFloat()
    public actual override fun toDouble(): Double = delegate.toDouble()
    public actual override fun toString(): String = delegate.toString()
    public actual override fun hashCode(): Int = delegate.hashCode()
    public actual override fun equals(other: Any?): Boolean = other is BigInteger && value == other.value

    public actual operator fun plus(other: BigInteger): BigInteger = BigInteger((delegate + other.delegate).toString())
    public actual operator fun minus(other: BigInteger): BigInteger = BigInteger((delegate -  other.delegate).toString())
    public actual fun toByteArray(): ByteArray = delegate.toByteArray()
}
