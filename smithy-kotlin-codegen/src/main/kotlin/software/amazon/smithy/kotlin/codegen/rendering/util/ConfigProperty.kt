/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.util

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.model.boxed
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.model.defaultValue
import software.amazon.smithy.kotlin.codegen.model.namespace

typealias CustomPropertyRenderer = (ConfigProperty, KotlinWriter) -> Unit

/**
 * Represents a configuration property (e.g. service client config)
 *
 * e.g.
 *
 * ```
 * val myProp = ConfigProperty {
 *     symbol = buildSymbol { ... }
 *     documentation = "my property documentation"
 *     addBaseClass(myBaseClass)
 * }
 * ```
 */
class ConfigProperty private constructor(builder: Builder) {
    init {
        if (builder.builderSymbol != null && builder.symbol != builder.builderSymbol) {
            requireNotNull(builder.toBuilderExpression) { "an expression mapping the immutable property back to the mutable builder is required when the builder symbol differs" }
        }
    }

    /**
     * The symbol (type) for the property
     *
     * NOTE: Use the extension properties on Symbol.Builder to set additional properties:
     * e.g.
     * ```
     * val symbol = Symbol.builder()
     *    .nullable = true // mark the symbol as nullable
     *    .defaultValue("foo") // set the default value for the property
     *    .build()
     * ```
     */
    val symbol: Symbol = requireNotNull(builder.symbol)

    /**
     * The symbol (type) for the builder implementation. Usually this is the same as the underlying symbol.
     * This can be used to generate a different builder symbol (e.g. `MutableList` vs `List`).
     */
    val builderSymbol: Symbol = builder.builderSymbol ?: symbol

    /**
     * The expression used to map immutable config back to a builder instance. This is usually not required
     * when the symbol types are the same as a simple assignment will work.
     *
     * e.g. `toBuilderExpression = ".toMutableList()"`
     */
    val toBuilderExpression: String? = builder.toBuilderExpression

    /**
     * Help text to be rendered for the property
     */
    val documentation: String? = builder.documentation

    /**
     * The name of the property to render to config
     */
    val propertyName: String = builder.name ?: symbol.name.replaceFirstChar { c -> c.lowercaseChar() }

    /**
     * Additional base classes config should inherit from
     *
     * NOTE: Adding 1 or more base classes will implicitly render the property with an `override` modifier
     */
    val baseClass: Symbol? = builder.baseClass

    /**
     * Additional base classes the config builder should inherit from
     *
     * NOTE: Adding 1 or more base classes will implicitly render the property with an `override` modifier
     */
    val builderBaseClass: Symbol? = builder.builderBaseClass

    /**
     * The configuration property type. This controls how the property is constructed and rendered
     */
    val propertyType: ConfigPropertyType = builder.propertyType

    /**
     * Additional symbols that should be imported when this property is generated. This is useful for
     * example when the [symbol] type has is an interface and has a default or constant value that
     * implements that type. The default value symbol also needs imported.
     */
    val additionalImports: List<Symbol> = builder.additionalImports

    /**
     * The priority order of rendering the property. Used to manage dependencies between configuration properties.
     */
    val order: Int = builder.order

    /**
     * Flag indicating if this property stems from some base class and needs an override modifier when rendered
     */
    internal val requiresOverride: Boolean
        get() = baseClass != null

    /**
     * Flag indicating if builder property stems from some base class and needs an override modifier when rendered
     */
    internal val builderRequiresOverride: Boolean
        get() = builderBaseClass != null

    companion object {
        operator fun invoke(block: Builder.() -> Unit): ConfigProperty =
            Builder().apply(block).build()

        /**
         * Convenience init for an integer symbol.
         * @param name The property name
         * @param defaultValue The default value the config property should have if not set (if not specified the
         * parameter is assumed nullable)
         * @param documentation Help text to render as documentation for the property
         * @param baseClass Base class the config class should inherit from (assumes this property
         * stems from this type)
         */
        fun Int(
            name: String,
            defaultValue: Int? = null,
            documentation: String? = null,
            baseClass: Symbol? = null,
        ): ConfigProperty =
            builtInProperty(name, builtInSymbol("Int", defaultValue?.toString()), documentation, baseClass)

        /**
         * Convenience init for a boolean symbol.
         * @param name The property name
         * @param defaultValue The default value the config property should have if not set (if not specified the
         * parameter is assumed nullable)
         * @param documentation Help text to render as documentation for the property
         * @param baseClass Base class the config class should inherit from (assumes this property
         * stems from this type)
         */
        fun Boolean(
            name: String,
            defaultValue: Boolean? = null,
            documentation: String? = null,
            baseClass: Symbol? = null,
        ): ConfigProperty =
            builtInProperty(name, builtInSymbol("Boolean", defaultValue?.toString()), documentation, baseClass)

        /**
         * Convenience init for a string symbol.
         * @param name The property name
         * @param defaultValue The default value the config property should have if not set (if not specified the
         * parameter is assumed nullable)
         * @param documentation Help text to render as documentation for the property
         * @param baseClass Base class the config class should inherit from (assumes this property
         * stems from this type)
         */
        fun String(
            name: String,
            defaultValue: String? = null,
            documentation: String? = null,
            baseClass: Symbol? = null,
        ): ConfigProperty =
            builtInProperty(name, builtInSymbol("String", defaultValue), documentation, baseClass)
    }

    class Builder {
        var symbol: Symbol? = null
        var builderSymbol: Symbol? = null
        var toBuilderExpression: String? = null

        // override the property name (defaults to symbol name)
        var name: String? = null
        var documentation: String? = null

        var baseClass: Symbol? = null
        var builderBaseClass: Symbol? = null

        var propertyType: ConfigPropertyType = ConfigPropertyType.SymbolDefault

        var additionalImports: List<Symbol> = emptyList()

        var order: Int = 0

        fun build(): ConfigProperty = ConfigProperty(this)
    }
}

private fun builtInSymbol(symbolName: String, defaultValue: String?): Symbol {
    val builder = Symbol.builder()
        .name(symbolName)

    if (defaultValue != null) {
        builder.defaultValue(defaultValue)
    } else {
        builder.boxed()
    }
    return builder.build()
}

private fun builtInProperty(
    name: String,
    symbol: Symbol,
    documentation: String?,
    baseClass: Symbol?,
): ConfigProperty =
    ConfigProperty {
        this.symbol = symbol
        this.name = name
        this.documentation = documentation
        this.baseClass = baseClass
    }

/**
 * Common client runtime related config properties
 */
object KotlinClientRuntimeConfigProperty {
    val HttpClientEngine: ConfigProperty
    val HttpInterceptors: ConfigProperty
    val IdempotencyTokenProvider: ConfigProperty
    val RetryStrategy: ConfigProperty
    val SdkLogMode: ConfigProperty
    val Tracer: ConfigProperty

    init {
        val httpClientConfigSymbol = buildSymbol {
            name = "HttpClientConfig"
            namespace(KotlinDependency.HTTP, "config")
        }

        HttpClientEngine = ConfigProperty {
            symbol = RuntimeTypes.Http.Engine.HttpClientEngine
            baseClass = httpClientConfigSymbol
            documentation = """
            Override the default HTTP client engine used to make SDK requests (e.g. configure proxy behavior, timeouts, concurrency, etc).
            NOTE: The caller is responsible for managing the lifetime of the engine when set. The SDK
            client will not close it when the client is closed.
            """.trimIndent()
            order = -100
        }

        IdempotencyTokenProvider = ConfigProperty {
            symbol = RuntimeTypes.Core.Client.IdempotencyTokenProvider

            baseClass = RuntimeTypes.Core.Client.IdempotencyTokenConfig

            documentation = """
            Override the default idempotency token generator. SDK clients will generate tokens for members
            that represent idempotent tokens when not explicitly set by the caller using this generator.
            """.trimIndent()
        }

        RetryStrategy = ConfigProperty {
            symbol = RuntimeTypes.Core.Retries.RetryStrategy
            name = "retryStrategy"
            documentation = """
                The [RetryStrategy] implementation to use for service calls. All API calls will be wrapped by the
                strategy.
            """.trimIndent()

            propertyType = ConfigPropertyType.RequiredWithDefault("StandardRetryStrategy()")

            additionalImports = listOf(RuntimeTypes.Core.Retries.StandardRetryStrategy)
        }

        SdkLogMode = ConfigProperty {
            symbol = buildSymbol {
                name = RuntimeTypes.Core.Client.SdkLogMode.name
                namespace = RuntimeTypes.Core.Client.SdkLogMode.namespace
                defaultValue = "SdkLogMode.Default"
                nullable = false
            }

            baseClass = RuntimeTypes.Core.Client.SdkClientConfig

            documentation = """
            Configure events that will be logged. By default clients will not output
            raw requests or responses. Use this setting to opt-in to additional debug logging.

            This can be used to configure logging of requests, responses, retries, etc of SDK clients.

            **NOTE**: Logging of raw requests or responses may leak sensitive information! It may also have
            performance considerations when dumping the request/response body. This is primarily a tool for
            debug purposes.
            """.trimIndent()
        }

        val tracingClientConfigSymbol = buildSymbol {
            name = "TracingClientConfig"
            namespace(KotlinDependency.TRACING_CORE)
        }

        // TODO support a nice DSL for this so that callers don't have to be aware of `DefaultTracer` if they don't want
        // to, they can just call `SomeClient { tracer { clientName = "Foo" } }` in the simple case.
        Tracer = ConfigProperty {
            symbol = RuntimeTypes.Tracing.Core.Tracer
            baseClass = tracingClientConfigSymbol
            documentation = """
                The tracer that is responsible for creating trace spans and wiring them up to a tracing backend (e.g.,
                a trace probe). By default, this will create a standard tracer that uses the service name for the root
                trace span and delegates to a logging trace probe (i.e.,
                `DefaultTracer(LoggingTraceProbe, "<service-name>")`).
            """.trimIndent()
            propertyType = ConfigPropertyType.Custom(render = { prop, writer ->
                val serviceName = writer.getContext("service.name")?.toString()
                    ?: throw CodegenException("The service.name context must be set for client config generation")
                writer.write(
                    """override val #1L: Tracer = builder.#1L ?: #2T(#3T, #4S)""",
                    prop.propertyName,
                    RuntimeTypes.Tracing.Core.DefaultTracer,
                    RuntimeTypes.Tracing.Core.LoggingTraceProbe,
                    serviceName,
                )
            },)
        }

        HttpInterceptors = ConfigProperty {
            name = "interceptors"
            val defaultValue = "${KotlinTypes.Collections.mutableListOf.fullName}()"
            val target = RuntimeTypes.Http.Interceptors.HttpInterceptor
            symbol = KotlinTypes.Collections.mutableList(target, isNullable = false, default = defaultValue)

            // built (immutable) property type changes from MutableList -> List
            propertyType = ConfigPropertyType.Custom(render = { prop, writer ->
                val immutablePropertyType = KotlinTypes.Collections.list(target, isNullable = false)
                writer.write("public val #1L: #2T = builder.#1L", prop.propertyName, immutablePropertyType)
            },)
            documentation = """
                Add an [aws.smithy.kotlin.runtime.client.Interceptor] that will have access to read and modify
                the request and response objects as they are processed by the SDK.
                Interceptors added using this method are executed in the order they are configured and are always 
                later than any added automatically by the SDK.
            """.trimIndent()
        }
    }
}
