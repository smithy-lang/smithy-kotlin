/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.util

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.model.buildSymbol

/**
 * Common client runtime related config properties
 */
object RuntimeConfigProperty {
    val HttpClientEngine = ConfigProperty {
        symbol = RuntimeTypes.HttpClient.Engine.HttpClientEngine
        baseClass = RuntimeTypes.HttpClient.Config.HttpClientConfig
        useNestedBuilderBaseClass()
        documentation = """
        Override the default HTTP client engine used to make SDK requests (e.g. configure proxy behavior, timeouts, concurrency, etc).
        NOTE: The caller is responsible for managing the lifetime of the engine when set. The SDK
        client will not close it when the client is closed.
        """.trimIndent()
        order = -100

        propertyType = ConfigPropertyType.Custom(
            render = { prop, writer ->
                writer.write(
                    "override val #1L: #2T = builder.#1L ?: #3T().#4T()",
                    prop.propertyName,
                    prop.symbol,
                    RuntimeTypes.HttpClientEngines.Default.DefaultHttpEngine,
                    RuntimeTypes.HttpClient.Engine.manage,
                )
            },
        )
    }

    val IdempotencyTokenProvider = ConfigProperty {
        symbol = RuntimeTypes.SmithyClient.IdempotencyTokenProvider

        baseClass = RuntimeTypes.SmithyClient.IdempotencyTokenConfig
        useNestedBuilderBaseClass()

        propertyType = ConfigPropertyType.RequiredWithDefault("${symbol!!.name}.Default")

        documentation = """
        Override the default idempotency token generator. SDK clients will generate tokens for members
        that represent idempotent tokens when not explicitly set by the caller using this generator.
        """.trimIndent()
    }

    val RetryPolicy = ConfigProperty {
        symbol = buildSymbol {
            name = "RetryPolicy<Any?>"
            reference(RuntimeTypes.Core.Retries.Policy.RetryPolicy, SymbolReference.ContextOption.USE)
        }
        name = "retryPolicy"
        documentation = """
            The policy to use for evaluating operation results and determining whether/how to retry.
        """.trimIndent()

        propertyType = ConfigPropertyType.RequiredWithDefault("StandardRetryPolicy.Default")
        baseClass = RuntimeTypes.SmithyClient.SdkClientConfig
        builderBaseClass = buildSymbol {
            name = "${baseClass!!.name}.Builder<Config>"
            namespace = baseClass!!.namespace
        }

        additionalImports = listOf(RuntimeTypes.Core.Retries.Policy.StandardRetryPolicy)
    }

    val RetryStrategy = ConfigProperty {
        symbol = RuntimeTypes.Core.Retries.RetryStrategy
        name = "retryStrategy"
        documentation = """
            The [RetryStrategy] implementation to use for service calls. All API calls will be wrapped by the
            strategy.
        """.trimIndent()

        propertyType = ConfigPropertyType.RequiredWithDefault("StandardRetryStrategy()")
        baseClass = RuntimeTypes.SmithyClient.SdkClientConfig
        builderBaseClass = buildSymbol {
            name = "${baseClass!!.name}.Builder<Config>"
            namespace = baseClass!!.namespace
        }

        additionalImports = listOf(RuntimeTypes.Core.Retries.StandardRetryStrategy)
    }

    val SdkLogMode = ConfigProperty {
        symbol = buildSymbol {
            name = RuntimeTypes.SmithyClient.SdkLogMode.name
            namespace = RuntimeTypes.SmithyClient.SdkLogMode.namespace
            defaultValue = "SdkLogMode.Default"
            nullable = false
        }

        baseClass = RuntimeTypes.SmithyClient.SdkClientConfig
        builderBaseClass = buildSymbol {
            name = "${baseClass!!.name}.Builder<Config>"
            namespace = baseClass!!.namespace
        }

        documentation = """
        Configure events that will be logged. By default clients will not output
        raw requests or responses. Use this setting to opt-in to additional debug logging.

        This can be used to configure logging of requests, responses, retries, etc of SDK clients.

        **NOTE**: Logging of raw requests or responses may leak sensitive information! It may also have
        performance considerations when dumping the request/response body. This is primarily a tool for
        debug purposes.
        """.trimIndent()
    }

    // TODO support a nice DSL for this so that callers don't have to be aware of `DefaultTracer` if they don't want
    // to, they can just call `SomeClient { tracer { clientName = "Foo" } }` in the simple case.
    // We could do this as an extension in the runtime off TracingClientConfig.Builder type...
    val Tracer = ConfigProperty {
        symbol = RuntimeTypes.Tracing.Core.Tracer
        baseClass = RuntimeTypes.Tracing.Core.TracingClientConfig
        useNestedBuilderBaseClass()

        documentation = """
            The tracer that is responsible for creating trace spans and wiring them up to a tracing backend (e.g.,
            a trace probe). By default, this will create a standard tracer that uses the service name for the root
            trace span and delegates to a logging trace probe (i.e.,
            `DefaultTracer(LoggingTraceProbe, "<service-name>")`).
        """.trimIndent()
        propertyType = ConfigPropertyType.Custom(
            render = { prop, writer ->
                val serviceName = writer.getContext("service.name")?.toString()
                    ?: throw CodegenException("The service.name context must be set for client config generation")
                writer.write(
                    """override val #1L: Tracer = builder.#1L ?: #2T(#3T, #4S)""",
                    prop.propertyName,
                    RuntimeTypes.Tracing.Core.DefaultTracer,
                    RuntimeTypes.Tracing.Core.LoggingTraceProbe,
                    serviceName,
                )
            },
        )
    }

    val HttpInterceptors = ConfigProperty {
        name = "interceptors"
        val defaultValue = "${KotlinTypes.Collections.mutableListOf.fullName}()"
        val target = RuntimeTypes.HttpClient.Interceptors.HttpInterceptor
        symbol = KotlinTypes.Collections.list(target, isNullable = false)
        builderSymbol = KotlinTypes.Collections.mutableList(target, isNullable = false, default = defaultValue)
        toBuilderExpression = ".toMutableList()"

        baseClass = RuntimeTypes.HttpClient.Config.HttpClientConfig
        useNestedBuilderBaseClass()

        documentation = """
            Add an [aws.smithy.kotlin.runtime.client.Interceptor] that will have access to read and modify
            the request and response objects as they are processed by the SDK.
            Interceptors added using this method are executed in the order they are configured and are always 
            later than any added automatically by the SDK.
        """.trimIndent()
    }
}
