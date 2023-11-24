/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.InternalApi

/**
 * Implementation of SHA-1 (Secure Hash Algorithm 1) hash function. See: https://csrc.nist.gov/projects/hash-functions
 */
@InternalApi
public actual class Sha1 actual constructor() : Sha1Base() {
    override fun update(input: ByteArray, offset: Int, length: Int) {
        TODO("Not yet implemented")
    }

    override fun digest(): ByteArray {
        TODO("Not yet implemented")
    }

    override fun reset() {
        TODO("Not yet implemented")
    }
}
