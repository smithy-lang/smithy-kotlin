/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.InternalApi
import java.security.MessageDigest

@InternalApi
public actual class Sha1 : Sha1Base() {
    private val md = MessageDigest.getInstance("SHA-1")
    override fun update(input: ByteArray, offset: Int, length: Int): Unit = md.update(input, offset, length)
    override fun digest(): ByteArray = md.digest()
    override fun reset(): Unit = md.reset()
}
