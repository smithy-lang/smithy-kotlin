/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.util

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.model.buildSymbol

/**
 * Common client runtime related config properties
 */
object RuntimeConfigProperty {
    val ClientName = ConfigProperty {
        name = "clientName"
        symbol = KotlinTypes.String
        baseClass = RuntimeTypes.SmithyClient.SdkClientConfig
        builderBaseClass = RuntimeTypes.SmithyClient.SdkClientConfig.nestedGenericBuilder
        documentation = """
            A reader-friendly name for the client.
        """.trimIndent()
        propertyType = ConfigPropertyType.Custom(
            render = { prop, writer ->
                writer.write("override val #1L: String = builder.#1L", prop.propertyName)
            },
            renderBuilder = { prop, writer ->
                val serviceName = writer.getContext("service.name")?.toString()
                    ?: throw CodegenException("The service.name context must be set for client config generation")

                prop.documentation?.let { writer.dokka(it) }
                writer.write("override var #L: String = #S", prop.propertyName, serviceName)
                writer.write("")
            },
        )
        order = -100
    }

    val HttpClient = ConfigProperty {
        name = "httpClient"
        symbol = RuntimeTypes.HttpClient.Engine.HttpClientEngine

        baseClass = RuntimeTypes.HttpClient.Config.HttpEngineConfig
        baseClassDelegate = Delegate(null, "builder.buildHttpEngineConfig()")

        builderBaseClass = RuntimeTypes.HttpClient.Config.HttpEngineConfig.nestedBuilder
        builderBaseClassDelegate = Delegate(
            RuntimeTypes.HttpClientEngines.Default.HttpEngineConfigImpl,
            "HttpEngineConfigImpl.BuilderImpl()",
        )

        propertyType = ConfigPropertyType.DoNotRender
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
        baseClass = RuntimeTypes.SmithyClient.RetryClientConfig
        builderBaseClass = RuntimeTypes.SmithyClient.RetryClientConfig.nestedBuilder

        additionalImports = listOf(RuntimeTypes.Core.Retries.Policy.StandardRetryPolicy)
    }

    val RetryStrategy = ConfigProperty {
        name = "retryStrategy"
        symbol = RuntimeTypes.Core.Retries.RetryStrategy
        documentation = """
            The [RetryStrategy] implementation to use for service calls. All API calls will be wrapped by the strategy.
        """.trimIndent()

        baseClass = RuntimeTypes.SmithyClient.RetryStrategyClientConfig
        baseClassDelegate = Delegate(null, "builder.buildRetryStrategyClientConfig()")

        builderBaseClass = RuntimeTypes.SmithyClient.RetryStrategyClientConfig.nestedBuilder
        builderBaseClassDelegate = Delegate(
            RuntimeTypes.SmithyClient.RetryStrategyClientConfigImpl,
            "RetryStrategyClientConfigImpl.BuilderImpl()",
        )

        propertyType = ConfigPropertyType.DoNotRender
    }

    val LogMode = ConfigProperty {
        symbol = buildSymbol {
            name = RuntimeTypes.SmithyClient.LogMode.name
            namespace = RuntimeTypes.SmithyClient.LogMode.namespace
        }
        propertyType = ConfigPropertyType.RequiredWithDefault("LogMode.Default")

        baseClass = RuntimeTypes.SmithyClient.SdkClientConfig
        builderBaseClass = RuntimeTypes.SmithyClient.SdkClientConfig.nestedGenericBuilder

        documentation = """
        Configure events that will be logged. By default clients will not output
        raw requests or responses. Use this setting to opt-in to additional debug logging.

        This can be used to configure logging of requests, responses, retries, etc of SDK clients.

        **NOTE**: Logging of raw requests or responses may leak sensitive information! It may also have
        performance considerations when dumping the request/response body. This is primarily a tool for
        debug purposes.
        """.trimIndent()
    }

    var TelemetryProvider = ConfigProperty {
        symbol = RuntimeTypes.Observability.TelemetryApi.TelemetryProvider
        baseClass = RuntimeTypes.Observability.TelemetryApi.TelemetryConfig
        useNestedBuilderBaseClass()

        documentation = """
            The telemetry provider used to instrument the SDK operations with. By default this will be a no-op
            implementation.
        """.trimIndent()

        propertyType = ConfigPropertyType.RequiredWithDefault("TelemetryProvider.None")
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

    val HttpAuthSchemes = ConfigProperty {
        name = "authSchemes"
        symbol = KotlinTypes.Collections.list(RuntimeTypes.Auth.HttpAuth.HttpAuthScheme, default = "emptyList()")
        baseClass = RuntimeTypes.Auth.HttpAuth.HttpAuthConfig
        useNestedBuilderBaseClass()
        documentation = """
            Register new or override default [HttpAuthScheme]s configured for this client. By default, the set
            of auth schemes configured comes from the service model. An auth scheme configured explicitly takes
            precedence over the defaults and can be used to customize identity resolution and signing for specific
            authentication schemes.
        """.trimIndent()
    }
}

private val Symbol.nestedBuilder: Symbol
    get() = buildSymbol {
        name = "${this@nestedBuilder.name}.Builder"
        namespace = this@nestedBuilder.namespace
    }

private val Symbol.nestedGenericBuilder: Symbol
    get() = buildSymbol {
        name = "${this@nestedGenericBuilder.name}.Builder<Config>"
        namespace = this@nestedGenericBuilder.namespace
    }
