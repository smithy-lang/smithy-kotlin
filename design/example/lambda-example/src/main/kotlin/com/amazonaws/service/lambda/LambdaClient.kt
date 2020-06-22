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
package com.amazonaws.service.lambda

import com.amazonaws.service.lambda.model.*
import com.amazonaws.service.runtime.SdkClient


interface LambdaClient: SdkClient {
    override val serviceName: String
        get() = "lambda"

    companion object {
        fun create(): LambdaClient = DefaultLambdaClient()
    }

    suspend fun invoke(input: InvokeRequest): InvokeResponse
    suspend fun invoke(block: InvokeRequest.DslBuilder.() -> Unit): InvokeResponse {
        val input = InvokeRequest{ block(this) }
        return invoke(input)
    }

    suspend fun createAlias(input: CreateAliasRequest): AliasConfiguration

    suspend fun createAlias(block: CreateAliasRequest.DslBuilder.() -> Unit): AliasConfiguration {
        val input = CreateAliasRequest{ block(this) }
        return createAlias(input)
    }
}