/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors.requestcompression.compressionalgorithms

import aws.smithy.kotlin.runtime.http.HttpBody

public actual class Gzip actual constructor() : CompressionAlgorithm {
    actual override val id: String
        get() = TODO("Not yet implemented")

    actual override suspend fun compress(stream: HttpBody): HttpBody {
        TODO("Not yet implemented")
    }

}