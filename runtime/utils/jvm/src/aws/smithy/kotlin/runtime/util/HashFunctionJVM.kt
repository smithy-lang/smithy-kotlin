/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.util

import java.security.MessageDigest

actual class Sha256 : HashFunction {
    private val md = MessageDigest.getInstance("SHA-256")
    override fun update(chunk: ByteArray) = md.update(chunk)
    override fun digest(): ByteArray = md.digest()
    override fun reset() = md.reset()
}

actual class MD5 : HashFunction {
    private val md = MessageDigest.getInstance("MD5")
    override fun update(chunk: ByteArray) = md.update(chunk)
    override fun digest(): ByteArray = md.digest()
    override fun reset() = md.reset()
}
