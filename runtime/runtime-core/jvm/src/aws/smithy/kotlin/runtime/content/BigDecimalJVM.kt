/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

public actual class BigDecimal actual constructor(public val value: String) : Number() {
    private val delegate = java.math.BigDecimal(value)

    public actual fun toPlainString(): String = delegate.toPlainString()
    actual override fun toByte(): Byte = delegate.toByte()
    actual override fun toDouble(): Double = delegate.toDouble()
    actual override fun toFloat(): Float = delegate.toFloat()
    actual override fun toInt(): Int = delegate.toInt()
    actual override fun toLong(): Long = delegate.toLong()
    actual override fun toShort(): Short = delegate.toShort()
    actual override fun equals(other: Any?): Boolean = (other is BigDecimal) && (value == other.value)
}
