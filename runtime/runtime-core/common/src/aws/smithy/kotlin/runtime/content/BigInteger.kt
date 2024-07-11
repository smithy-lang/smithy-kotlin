/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

/**
 * An arbitrarily large signed integer
 * @param value the string representation of this large integer
 */
public expect class BigInteger(value: String) : Number, Comparable<BigInteger> {
    /**
     * Create an instance of [BigInteger] from a [ByteArray]
     * @param bytes ByteArray representing the large integer
     */
    public constructor(bytes: ByteArray)

    override fun toByte(): Byte
    override fun toLong(): Long
    override fun toShort(): Short
    override fun toInt(): Int
    override fun toFloat(): Float
    override fun toDouble(): Double
    override fun toString(): String
    override fun hashCode(): Int
    override fun equals(other: Any?): Boolean
    public operator fun plus(other: BigInteger): BigInteger
    public operator fun minus(other: BigInteger): BigInteger
    public fun toByteArray(): ByteArray
    public override operator fun compareTo(other: BigInteger): Int
}
