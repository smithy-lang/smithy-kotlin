/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.InternalApi
import java.util.zip.CRC32

@InternalApi
public actual class Crc32 : Crc32Base() {
    private val md = CRC32()

    override fun update(input: ByteArray, offset: Int, length: Int): Unit = md.update(input, offset, length)

    override fun digestValue(): UInt = md.value.toUInt()

    override fun reset(): Unit = md.reset()
}
