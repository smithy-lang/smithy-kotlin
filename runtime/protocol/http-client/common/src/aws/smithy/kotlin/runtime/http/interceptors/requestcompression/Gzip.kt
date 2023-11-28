/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors.requestcompression

import aws.smithy.kotlin.runtime.http.request.HttpRequest

public expect class Gzip() : CompressionAlgorithm {
    override val id: String
    override val contentEncoding: String
    override suspend fun compress(request: HttpRequest): HttpRequest
}
