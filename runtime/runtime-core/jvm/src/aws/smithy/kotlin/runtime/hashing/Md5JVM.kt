/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.InternalApi
import java.security.MessageDigest

@InternalApi
public actual class Md5 : Md5Base() {
    private val md = MessageDigest.getInstance("MD5")
    actual override fun update(input: ByteArray, offset: Int, length: Int): Unit = md.update(input, offset, length)
    public actual fun update(input: Byte): Unit = md.update(input)
    actual override fun digest(): ByteArray = md.digest()
    actual override fun reset(): Unit = md.reset()
}
