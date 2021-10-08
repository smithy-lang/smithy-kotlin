/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.retries

import software.amazon.smithy.codegen.core.SymbolReference.*
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.integration.SectionId
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.model.namespace
import software.amazon.smithy.kotlin.codegen.rendering.ClientConfigProperty
import software.amazon.smithy.kotlin.codegen.rendering.protocol.HttpFeatureMiddleware
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolMiddleware

object RetryConfigSection : SectionId

/**
 * Adds retry wrappers around operation invocations, driven by client config (which specifies the [RetryStrategy]) and
 * a middleware (which specifies the [RetryPolicy][aws.smithy.kotlin.runtime.retries.RetryPolicy]). This class is
 * `open` and may be subclassed to customize the strategy and/or policy. Note that codegen which specifies a subclass of
 * this should probably configure [replacesIntegrations] to remove the base implementation—otherwise, multiple copies of
 * the config properties may be included in client config.
 */
class StandardRetryIntegration : KotlinIntegration {
    override fun additionalServiceConfigProps(ctx: CodegenContext): List<ClientConfigProperty> =
        listOf(
            ClientConfigProperty {
                val retryStrategyBlock = """
                    run {
                        val strategyOptions = StandardRetryStrategyOptions.Default
                        val tokenBucket = StandardRetryTokenBucket(StandardRetryTokenBucketOptions.Default)
                        val delayer = ExponentialBackoffWithJitter(ExponentialBackoffWithJitterOptions.Default)
                        StandardRetryStrategy(strategyOptions, tokenBucket, delayer)
                    }
                """.trimIndent()

                symbol = buildSymbol {
                    name = "RetryStrategy"
                    namespace(KotlinDependency.CORE, "retries")
                    nullable = false
                    reference(RuntimeTypes.Core.Retries.Impl.StandardRetryStrategy, ContextOption.USE)
                    reference(RuntimeTypes.Core.Retries.Impl.StandardRetryStrategyOptions, ContextOption.USE)
                    reference(RuntimeTypes.Core.Retries.Impl.StandardRetryTokenBucket, ContextOption.USE)
                    reference(RuntimeTypes.Core.Retries.Impl.StandardRetryTokenBucketOptions, ContextOption.USE)
                    reference(RuntimeTypes.Core.Retries.Impl.ExponentialBackoffWithJitter, ContextOption.USE)
                    reference(RuntimeTypes.Core.Retries.Impl.ExponentialBackoffWithJitterOptions, ContextOption.USE)
                }
                name = "retryStrategy"
                documentation = """
                    The [RetryStrategy] implementation to use for service calls. All API calls will be wrapped by the
                    strategy.
                """.trimIndent()
                constantValue = retryStrategyBlock
            }
        )

    override fun customizeMiddleware(
        ctx: ProtocolGenerator.GenerationContext,
        resolved: List<ProtocolMiddleware>,
    ): List<ProtocolMiddleware> = resolved + middleware

    private val middleware = object : HttpFeatureMiddleware() {
        override val name: String = "RetryFeature"

        override fun renderConfigure(writer: KotlinWriter) {
            writer.declareSection(RetryConfigSection) {
                addImport(RuntimeTypes.Http.Middlware.RetryFeature)
                addImport(RuntimeTypes.Core.Retries.Impl.StandardRetryPolicy)

                write("strategy = config.retryStrategy")
                write("policy = StandardRetryPolicy.Default")
            }
        }
    }
}
