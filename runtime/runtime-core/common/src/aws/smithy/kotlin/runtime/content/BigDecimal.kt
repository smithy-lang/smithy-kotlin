/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

public expect class BigDecimal(value: String) : Number {
    override fun toByte(): Byte
    override fun toDouble(): Double
    override fun toFloat(): Float
    override fun toShort(): Short
    override fun toInt(): Int
    override fun toLong(): Long
    public fun toPlainString(): String
    override fun equals(other: Any?): Boolean
}
