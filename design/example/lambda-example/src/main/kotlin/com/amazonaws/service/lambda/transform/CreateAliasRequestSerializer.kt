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

import com.amazonaws.service.lambda.model.CreateAliasRequest
import com.amazonaws.service.runtime.HttpSerialize
import com.amazonaws.service.runtime.Serializer
import software.aws.clientrt.http.HttpMethod
import software.aws.clientrt.http.content.ByteArrayContent
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.request.headers
import software.aws.clientrt.http.request.url



class CreateAliasRequestSerializer(val input: CreateAliasRequest): HttpSerialize {
    override suspend fun serialize(builder: HttpRequestBuilder, serializer: Serializer) {
        // URI
        builder.method = HttpMethod.POST
        builder.url {
            // NOTE: Individual serializers do not need to concern themselves with protocol/host
            path = "/2015-03-31/functions/${input.functionName}/aliases"
        }

        // Headers
        builder.headers {
            append("Content-Type", "application/json")
        }

        // payload
        // FIXME - this is responsibility of the serializer which is not defined yet
        // for now we'll hack it together using a string builder
        val content = buildString {
            append("{")
            if (input.description != null) {
                append("\"Description\": \"${input.description}\",")
            }

            // version is requierd
            if (input.functionVersion != null) {
                append("\"FunctionVersion\": \"${input.functionVersion}\",")
            }

            if (input.routingConfig != null) {
                append("\"RoutingConfig\": {")
                var cnt = 0
                input.routingConfig.forEach(){ k, v ->
                    cnt++
                    append("\"$k\":\"$v\"")
                    if (cnt < input.routingConfig.size) append(",")
                }
                append("},")
            }

            // name is required
            if (input.name != null) {
                append("\"Name\": \"${input.functionVersion}\"")
            }
            append("}")
        }

        builder.body = ByteArrayContent(content.toByteArray())
    }
}
