/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.InternalApi

/**
 * Implementation of RFC1321 MD5 digest
 */
@InternalApi
public actual class Md5 actual constructor() : Md5Base() {
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
