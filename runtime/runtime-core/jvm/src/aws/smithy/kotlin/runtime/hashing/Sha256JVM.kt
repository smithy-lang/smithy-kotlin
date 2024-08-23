/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.InternalApi
import java.security.MessageDigest

@InternalApi
public actual class Sha256 : Sha256Base() {
    private val md = MessageDigest.getInstance("SHA-256")
    actual override fun update(input: ByteArray, offset: Int, length: Int): Unit = md.update(input, offset, length)
    actual override fun digest(): ByteArray = md.digest()
    actual override fun reset(): Unit = md.reset()
}
