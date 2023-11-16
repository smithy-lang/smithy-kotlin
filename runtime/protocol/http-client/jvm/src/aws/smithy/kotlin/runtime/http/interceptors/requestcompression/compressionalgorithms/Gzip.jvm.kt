/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors.requestcompression.compressionalgorithms

import aws.smithy.kotlin.runtime.http.HttpBody

internal actual class Gzip : CompressionAlgorithm {
    actual override val id: String
        get() = "gzip"

    actual override suspend fun compress(stream: HttpBody): HttpBody {
        when(stream) {
            is HttpBody.SourceContent -> {
                stream.readFrom()
                stream.
            }
            is HttpBody.ChannelContent -> {

            }
            is HttpBody.Bytes -> {

            }
            is HttpBody.Empty -> stream
        }
        TODO("Finish implementation")
    }
}