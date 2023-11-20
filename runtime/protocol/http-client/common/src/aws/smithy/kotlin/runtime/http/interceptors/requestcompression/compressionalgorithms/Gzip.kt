/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors.requestcompression.compressionalgorithms

import aws.smithy.kotlin.runtime.http.HttpBody

public expect class Gzip() : CompressionAlgorithm {
    override val id: String
    override suspend fun compress(stream: HttpBody): HttpBody
}
