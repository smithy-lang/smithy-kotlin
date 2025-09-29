/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.InternalApi
import aws.sdk.kotlin.crt.util.hashing.Sha256 as CrtSha256

@InternalApi
public actual class Sha256 : Sha256Base() {
    private val delegate = CrtSha256()
    actual override fun update(input: ByteArray, offset: Int, length: Int): Unit = delegate.update(input, offset, length)
    actual override fun digest(): ByteArray = delegate.digest()
    actual override fun reset(): Unit = delegate.reset()
}
