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
    override val id: String // expect class members must be explicitly overridden in K2: https://kotlinlang.slack.com/archives/C03PK0PE257/p1700127129049459?thread_ts=1700121556.811959&cid=C03PK0PE257
    override val contentEncoding: String
    override fun compress(stream: ByteStream): ByteStream
}
