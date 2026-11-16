/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.testing

public actual class RandomTempFile actual constructor(
    sizeInBytes: Long,
    filename: String,
    binaryData: Boolean,
) {
    init {
        TODO("Not yet implemented on wasmJs: filesystem")
    }
}
