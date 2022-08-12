/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.benchmarks.http

import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineConfig
import aws.smithy.kotlin.runtime.http.engine.ktor.KtorEngine
import io.ktor.client.engine.okhttp.*
import okhttp3.ConnectionPool
import okhttp3.Protocol
import java.util.concurrent.TimeUnit
import kotlin.time.toJavaDuration

internal fun KtorOkHttpEngine(config: HttpClientEngineConfig = HttpClientEngineConfig.Default): HttpClientEngine {
    val okHttpEngine = OkHttp.create {
        config {
            connectTimeout(config.connectTimeout.toJavaDuration())
            readTimeout(config.socketReadTimeout.toJavaDuration())
            writeTimeout(config.socketWriteTimeout.toJavaDuration())
            val pool = ConnectionPool(
                maxIdleConnections = config.maxConnections.toInt(),
                keepAliveDuration = config.connectionIdleTimeout.inWholeMilliseconds,
                TimeUnit.MILLISECONDS,
            )
            connectionPool(pool)

            if (config.alpn.isNotEmpty()) {
                val protocols = config.alpn.mapNotNull {
                    when (it) {
                        aws.smithy.kotlin.runtime.http.engine.AlpnId.HTTP1_1 -> Protocol.HTTP_1_1
                        aws.smithy.kotlin.runtime.http.engine.AlpnId.HTTP2 -> Protocol.HTTP_2
                        else -> null
                    }
                }
                protocols(protocols)
            }
        }
    }

    return KtorEngine(okHttpEngine)
}
