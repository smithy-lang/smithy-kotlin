/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors.requestcompression

import aws.smithy.kotlin.runtime.http.request.HttpRequest

public actual class Gzip actual constructor() : CompressionAlgorithm {
    actual override val id: String
        get() = TODO("Not yet implemented")

    actual override val contentEncoding: String
        get() = TODO("Not yet implemented")

    actual override suspend fun compress(request: HttpRequest): HttpRequest {
        TODO("Not yet implemented")
    }
}
