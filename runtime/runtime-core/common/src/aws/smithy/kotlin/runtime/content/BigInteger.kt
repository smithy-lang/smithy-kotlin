/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

public expect class BigInteger(value: String) : Number {
    override fun toByte(): Byte
    override fun toLong(): Long
    override fun toShort(): Short
    override fun toInt(): Int
    override fun toFloat(): Float
    override fun toDouble(): Double
    override fun toString(): String
    override fun hashCode(): Int
    override fun equals(other: Any?): Boolean
}
