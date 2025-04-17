/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.http.HttpCall
import aws.smithy.kotlin.runtime.http.config.EngineFactory
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.engine.callContext
import aws.smithy.kotlin.runtime.http.engine.internal.HttpClientMetrics
import aws.smithy.kotlin.runtime.http.engine.okhttp.adapters.ClientFactoryAdapter
import aws.smithy.kotlin.runtime.http.engine.okhttp.adapters.DefaultClientFactoryAdapter
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import okhttp3.OkHttpClient

/**
 * [aws.smithy.kotlin.runtime.http.engine.HttpClientEngine] based on OkHttp.
 */
public class OkHttpEngine internal constructor(
    override val config: OkHttpEngineConfig,
    clientFactoryAdapter: ClientFactoryAdapter,
) : HttpClientEngineBase("OkHttp") {
    public constructor() : this(OkHttpEngineConfig.Default, DefaultClientFactoryAdapter)

    public constructor(config: OkHttpEngineConfig) : this(config, DefaultClientFactoryAdapter)

    public companion object : EngineFactory<OkHttpEngineConfig.Builder, OkHttpEngine> {
        /**
         * Initializes a new [OkHttpEngine] via a DSL builder block
         * @param block A receiver lambda which sets the properties of the config to be built
         */
        public operator fun invoke(block: OkHttpEngineConfig.Builder.() -> Unit): OkHttpEngine =
            OkHttpEngine(OkHttpEngineConfig(block))

        override val engineConstructor: (OkHttpEngineConfig.Builder.() -> Unit) -> OkHttpEngine = Companion::invoke
    }

    private val metrics = HttpClientMetrics(TELEMETRY_SCOPE, config.telemetryProvider)
    private val client = clientFactoryAdapter.buildClient(config, metrics)

    override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall =
        client.roundTrip(context, callContext(), request)

    override fun shutdown() {
        client.close()
        metrics.close()
    }
}

@InternalApi
public fun OkHttpEngineConfig.buildClient(
    metrics: HttpClientMetrics,
): OkHttpClient = error("Foo!")
