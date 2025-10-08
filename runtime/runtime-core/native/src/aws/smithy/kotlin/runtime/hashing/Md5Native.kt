/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.InternalApi
import aws.sdk.kotlin.crt.util.hashing.Md5 as CrtMd5

/**
 * Implementation of RFC1321 MD5 digest
 */
@InternalApi
public actual class Md5 actual constructor() : Md5Base() {
    private val delegate = CrtMd5()
    actual override fun update(input: ByteArray, offset: Int, length: Int): Unit = delegate.update(input, offset, length)
    actual override fun digest(): ByteArray = delegate.digest()
    actual override fun reset(): Unit = delegate.reset()
}
