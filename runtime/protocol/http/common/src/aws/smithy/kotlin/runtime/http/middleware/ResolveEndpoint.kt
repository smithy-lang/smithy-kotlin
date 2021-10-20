/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.operation.EndpointResolver
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.operation.SdkHttpOperation
import aws.smithy.kotlin.runtime.http.operation.getLogger
import aws.smithy.kotlin.runtime.util.InternalApi

/**
 *  Http feature for resolving the service endpoint.
 */
@InternalApi
public class ResolveEndpoint(
    config: Config
) : Feature {

    private val resolver: EndpointResolver = requireNotNull(config.resolver) { "EndpointResolver must not be null" }

    public class Config {
        /**
         * The resolver to use
         */
        public var resolver: EndpointResolver? = null
    }

    public companion object Feature : HttpClientFeatureFactory<Config, ResolveEndpoint> {
        override val key: FeatureKey<ResolveEndpoint> = FeatureKey("ResolveEndpoint")

        override fun create(block: Config.() -> Unit): ResolveEndpoint {
            val config = Config().apply(block)
            return ResolveEndpoint(config)
        }
    }

    override fun <I, O> install(operation: SdkHttpOperation<I, O>) {
        operation.execution.mutate.intercept { req, next ->
            val endpoint = resolver.resolve()
            val hostPrefix = req.context.getOrNull(HttpOperationContext.HostPrefix)
            val hostname = if (hostPrefix != null) "${hostPrefix}${endpoint.uri.host}" else endpoint.uri.host
            req.subject.url.scheme = endpoint.uri.scheme
            req.subject.url.host = hostname
            req.subject.url.port = endpoint.uri.port
            req.subject.headers["Host"] = hostname
            if (endpoint.uri.path.isNotBlank()) {
                val pathPrefix = endpoint.uri.path.removeSuffix("/")
                val original = req.subject.url.path.removePrefix("/")
                req.subject.url.path = "$pathPrefix/$original"
            }

            val logger = req.context.getLogger("ResolveEndpoint")
            logger.debug { "resolved endpoint: $endpoint" }
            next.call(req)
        }
    }
}
