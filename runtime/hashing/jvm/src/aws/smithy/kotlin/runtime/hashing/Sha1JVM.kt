/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.hashing

import java.security.MessageDigest

actual class Sha1 : Sha1Base() {
    private val md = MessageDigest.getInstance("SHA-1")
    override fun update(input: ByteArray) = md.update(input)
    override fun digest(): ByteArray = md.digest()
    override fun reset() = md.reset()
}
