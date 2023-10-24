/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.InternalApi

@InternalApi
public actual class Sha256 : Sha256Base() {
    override fun update(input: ByteArray, offset: Int, length: Int): Unit = TODO("native not supported")
    override fun digest(): ByteArray = TODO("native not supported")
    override fun reset(): Unit = TODO("native not supported")
}
