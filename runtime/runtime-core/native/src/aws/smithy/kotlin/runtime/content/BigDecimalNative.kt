/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

public actual class BigDecimal actual constructor(value: String) : Number() {
    public actual fun toPlainString(): String {
        TODO("Not yet implemented")
    }

    actual override fun toByte(): Byte {
        TODO("Not yet implemented")
    }

    actual override fun toDouble(): Double {
        TODO("Not yet implemented")
    }

    actual override fun toFloat(): Float {
        TODO("Not yet implemented")
    }

    actual override fun toInt(): Int {
        TODO("Not yet implemented")
    }

    actual override fun toLong(): Long {
        TODO("Not yet implemented")
    }

    actual override fun toShort(): Short {
        TODO("Not yet implemented")
    }
}
