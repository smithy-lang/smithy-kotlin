/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.InternalApi

@InternalApi
public actual class GzipSdkSource actual constructor(public val source: SdkSource) : SdkSource {
    actual override fun read(sink: SdkBuffer, limit: Long): Long = TODO("Not yet implemented on wasmJs: gzip")

    actual override fun close(): Unit = source.close()
}
