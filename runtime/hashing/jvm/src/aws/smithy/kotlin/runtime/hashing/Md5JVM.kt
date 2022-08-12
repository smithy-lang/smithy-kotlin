/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import java.security.MessageDigest

public actual class Md5 : Md5Base() {
    private val md = MessageDigest.getInstance("MD5")
    override fun update(input: ByteArray, offset: Int, length: Int): Unit = md.update(input, offset, length)
    override fun digest(): ByteArray = md.digest()
    override fun reset(): Unit = md.reset()
}
