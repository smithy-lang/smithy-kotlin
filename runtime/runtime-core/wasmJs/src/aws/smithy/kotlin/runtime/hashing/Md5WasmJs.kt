/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.InternalApi

@InternalApi
public actual class Md5 actual constructor() : Md5Base() {
    actual override fun update(input: ByteArray, offset: Int, length: Int): Unit = TODO("Not yet implemented on wasmJs: hashing")
    actual override fun digest(): ByteArray = TODO("Not yet implemented on wasmJs: hashing")
    actual override fun reset(): Unit = TODO("Not yet implemented on wasmJs: hashing")
}
