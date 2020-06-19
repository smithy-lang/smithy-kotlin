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
package com.amazonaws.service.lambda.transform

import com.amazonaws.service.runtime.*
import com.amazonaws.service.lambda.model.InvokeResponse
import software.aws.clientrt.http.HttpBody
import software.aws.clientrt.http.response.HttpResponse
import software.aws.clientrt.serde.Deserializer


class InvokeResponseDeserializer: HttpDeserialize {
    override suspend fun deserialize(response: HttpResponse, deserializer: Deserializer): InvokeResponse {
        println("""
           Received: 
           Status: ${response.status}
           Headers: ${response.headers.entries()}
        """.trimIndent())

        val body = response.body
        println("(${Thread.currentThread().name}) deserializer: reading body")
        val respPayload = when(body) {
            is HttpBody.Bytes -> body.bytes()
            is HttpBody.Streaming -> body.readFrom().readAll()
            else -> null
        }

        return InvokeResponse{
            statusCode = response.status.value
            // functionError: String?
            logResult = response.headers["X-Amz-Log-Result"]
            executedVersion = response.headers["X-Amz-Executed-Version"]
            payload = respPayload
        }
    }
}