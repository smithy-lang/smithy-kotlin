/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.amazonaws.service.s3.transform

import com.amazonaws.service.s3.model.PutObjectRequest
import software.aws.clientrt.http.HttpBody
import software.aws.clientrt.http.HttpMethod
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.request.headers
import software.aws.clientrt.http.request.url
import com.amazonaws.service.runtime.*


class PutObjectRequestSerializer(val input: PutObjectRequest): HttpSerialize {
    override suspend fun serialize(builder: HttpRequestBuilder, serializer: Serializer) {
        // URI
        builder.method = HttpMethod.POST
        builder.url {
            // NOTE: Individual serializers do not need to concern themselves with protocol/host
            path = "/putObject/"
        }

        // Headers
        builder.headers {
            append("Content-Type", "application/x-amz-json-1.1")

            // optional header params
            if (input.cacheControl != null) append("Cache-Control", input.cacheControl)
            if (input.contentDisposition != null) append("Content-Disposition", input.contentDisposition)
            if (input.contentLength != null) append("Content-Length", input.contentLength.toString())
        }

        // payload
        builder.body = input.body?.toHttpBody() ?: HttpBody.Empty
    }
}

