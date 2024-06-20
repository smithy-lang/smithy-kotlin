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
}

/**
 * FIXME typealias does not work ...
 * e: file:///Users/lauzmata/smithy-kotlin/runtime/runtime-core/jvm/src/aws/smithy/kotlin/runtime/content/BigDecimalJVM.kt:7:25 Actual class 'actual typealias BigDecimal = BigDecimal' has no corresponding members for expected class members:
 *
 *     expect fun toByte(): Byte
 *
 *     The following declaration is incompatible because modality is different:
 *         fun toByte(): Byte
 *
 *     expect fun toShort(): Short
 *
 *     The following declaration is incompatible because modality is different:
 *         fun toShort(): Short
 *
 */
// public actual typealias BigDecimal = java.math.BigDecimal
