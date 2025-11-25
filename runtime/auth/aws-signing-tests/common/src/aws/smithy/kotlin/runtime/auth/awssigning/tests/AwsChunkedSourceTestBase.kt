/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awssigning.tests

import aws.smithy.kotlin.runtime.auth.awssigning.AwsChunkedSource
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigner
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigningConfig
import aws.smithy.kotlin.runtime.http.DeferredHeaders
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.source
import kotlinx.coroutines.Dispatchers

private val chunkedSourceFactory = object : AwsChunkedReaderFactory {
    override fun create(
        data: ByteArray,
        signer: AwsSigner,
        signingConfig: AwsSigningConfig,
        previousSignature: ByteArray,
        trailingHeaders: DeferredHeaders,
    ): AwsChunkedTestReader {
        val source = data.source()
        val chunked = AwsChunkedSource(
            source,
            signer,
            signingConfig,
            previousSignature,
            trailingHeaders,
            Dispatchers.IO, // Cannot use default TestDispatcher because it doesn't support parallelism and causes hangs
        )
        return object : AwsChunkedTestReader {
            override fun isClosedForRead(): Boolean {
                val sink = SdkBuffer()
                val rc = chunked.read(sink, Long.MAX_VALUE)
                return rc == -1L
            }
            override suspend fun read(sink: SdkBuffer, limit: Long): Long = chunked.read(sink, limit)
            override fun close() {
                source.close()
            }
        }
    }
}

abstract class AwsChunkedSourceTestBase : AwsChunkedTestBase(chunkedSourceFactory)
