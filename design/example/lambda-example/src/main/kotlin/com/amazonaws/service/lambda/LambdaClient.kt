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
import software.aws.clientrt.config.IdempotencyTokenProvider
import software.aws.clientrt.SdkClient
import software.aws.clientrt.config.ServiceClientIdempotencyTokenConfig
import software.aws.clientrt.http.engine.HttpClientEngine
import software.aws.clientrt.http.engine.ServiceClientHttpConfig
import software.aws.clientrt.logging.KLogger
import software.aws.clientrt.logging.KotlinLogging
import software.aws.clientrt.logging.ServiceClientLoggingConfig


interface LambdaClient: SdkClient {
    override val serviceName: String
        get() = "lambda"

    companion object {
        fun build(block: Config.() -> Unit = {}): LambdaClient {
            val config = Config().apply(block)
            return DefaultLambdaClient(config)
        }
    }

    data class Config(
        override val httpConfig: HttpClientEngine? = null,
        override val idempotencyTokenProvider: IdempotencyTokenProvider? = null,
        override val kotlinLoggingProvider: () -> KLogger? = { null }
    ) : ServiceClientHttpConfig, ServiceClientIdempotencyTokenConfig, ServiceClientLoggingConfig

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