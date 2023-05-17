/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine

import aws.smithy.kotlin.runtime.http.config.EngineFactory
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.operation.ExecutionContext

/**
 * Provides an implementation of an [HttpClientEngine] which is guaranteed never to make actual network calls. (Attempts
 * to make network calls result in an exception.) Use this when you need to configure a service client, but you are
 * certain no HTTP engine is actually needed.
 */
public class NoHttpEngine : HttpClientEngineBase("none") {
    public companion object : EngineFactory<HttpClientEngineConfig.Builder, NoHttpEngine> {
        override val engineConstructor: (HttpClientEngineConfig.Builder.() -> Unit) -> NoHttpEngine =
            { _ -> NoHttpEngine() } // Config is ignored
    }

    override val config: HttpClientEngineConfig = HttpClientEngineConfig.Default

    override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall {
        error("The `NoHttpEngine` client was configured but an HTTP call was invoked.")
    }
}
