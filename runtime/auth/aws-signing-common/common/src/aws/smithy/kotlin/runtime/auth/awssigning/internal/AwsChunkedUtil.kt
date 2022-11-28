/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awssigning.internal

import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.io.SdkBuffer

/**
 * Chunk size used by Transfer-Encoding `aws-chunked`
 */
public const val CHUNK_SIZE_BYTES: Int = 65_536

internal fun SdkBuffer.writeTrailers(
    trailers: Headers,
    signature: String,
) {
    trailers
        .entries()
        .sortedBy { entry -> entry.key.lowercase() }
        .forEach { entry ->
            writeUtf8(entry.key)
            writeUtf8(":")
            writeUtf8(entry.value.joinToString(",") { v -> v.trim() })
            writeUtf8("\r\n")
        }
    writeUtf8("x-amz-trailer-signature:${signature}\r\n")
}
