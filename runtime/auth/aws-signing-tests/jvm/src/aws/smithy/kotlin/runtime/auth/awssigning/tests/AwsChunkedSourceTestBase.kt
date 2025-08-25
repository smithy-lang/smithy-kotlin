/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awssigning.tests
import aws.smithy.kotlin.runtime.auth.awssigning.AwsChunkedSource
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.source

val AwsChunkedReaderFactory.Companion.Source: AwsChunkedReaderFactory
    get() = AwsChunkedReaderFactory { data, signer, signingConfig, previousSignature, trailingHeaders ->
        val source = data.source()
        val chunked = AwsChunkedSource(source, signer, signingConfig, previousSignature, trailingHeaders)
        object : AwsChunkedTestReader {
            override fun isClosedForRead(): Boolean {
                val sink = SdkBuffer()
                val rc = chunked.read(sink, Long.MAX_VALUE)
                return rc == -1L
            }
            override suspend fun read(sink: SdkBuffer, limit: Long): Long = chunked.read(sink, limit)
        }
    }

public abstract class AwsChunkedSourceTestBase : AwsChunkedTestBase(AwsChunkedReaderFactory.Source)
