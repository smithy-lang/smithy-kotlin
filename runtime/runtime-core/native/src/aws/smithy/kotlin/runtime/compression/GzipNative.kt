/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.compression

import aws.smithy.kotlin.runtime.content.ByteStream
import platform.zlib.*

/**
 * The gzip compression algorithm.
 * Used to compress http requests.
 *
 * See: https://en.wikipedia.org/wiki/Gzip
 */
public actual class Gzip : CompressionAlgorithm {
    actual override val id: String = "gzip"
    actual override val contentEncoding: String = "gzip"

    actual override fun compress(stream: ByteStream): ByteStream {
        TODO("Not yet implemented")
    }
}
