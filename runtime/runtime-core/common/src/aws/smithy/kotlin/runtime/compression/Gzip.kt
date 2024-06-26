/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.compression

import aws.smithy.kotlin.runtime.content.ByteStream

/**
 * The gzip compression algorithm.
 * Used to compress http requests.
 *
 * See: https://en.wikipedia.org/wiki/Gzip
 */
public expect class Gzip() : CompressionAlgorithm {
    override val id: String
    override val contentEncoding: String
    override fun compress(stream: ByteStream): ByteStream
}
