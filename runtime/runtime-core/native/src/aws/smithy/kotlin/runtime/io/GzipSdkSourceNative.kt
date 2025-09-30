/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.InternalApi

/**
 * Wraps the SdkSource so that it compresses into gzip format with each read.
 */
@InternalApi
public actual class GzipSdkSource actual constructor(source: SdkSource) : SdkSource {
    actual override fun read(sink: SdkBuffer, limit: Long): Long {
        TODO("Not yet implemented")
    }

    actual override fun close() {
        TODO("Not yet implemented")
    }
}
