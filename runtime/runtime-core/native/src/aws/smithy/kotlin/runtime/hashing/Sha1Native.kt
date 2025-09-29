/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.InternalApi
import aws.sdk.kotlin.crt.util.hashing.Sha1 as CrtSha1

/**
 * Implementation of SHA-1 (Secure Hash Algorithm 1) hash function. See: https://csrc.nist.gov/projects/hash-functions
 */
@InternalApi
public actual class Sha1 actual constructor() : Sha1Base() {
    private val delegate = CrtSha1()

    actual override fun update(input: ByteArray, offset: Int, length: Int): Unit = delegate.update(input, offset, length)
    actual override fun digest(): ByteArray = delegate.digest()
    actual override fun reset(): Unit = delegate.reset()
}
