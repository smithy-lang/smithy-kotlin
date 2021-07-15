/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.model.boxed
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.model.defaultValue
import software.amazon.smithy.kotlin.codegen.model.namespace

/**
 * Represents a service client config property to be added to the generated client
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
class ClientConfigProperty private constructor(builder: Builder) {

    /**
     * The symbol (type) for the property
     *
     * NOTE: Use the extension properties on Symbol.Builder to set additional properties:
     * e.g.
     * ```
     * val symbol = Symbol.builder()
     *    .boxed() // mark the symbol as nullable
     *    .defaultValue("foo") // set the default value for the property
     *    .build()
     * ```
     */
    val symbol: Symbol = requireNotNull(builder.symbol)

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
     * Flag indicating if this property stems from some base class and needs an override modifier when rendered
     */
    internal val requiresOverride: Boolean
        get() = baseClass != null

    companion object {
        operator fun invoke(block: Builder.() -> Unit): ClientConfigProperty =
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
            baseClass: Symbol? = null
        ): ClientConfigProperty =
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
            baseClass: Symbol? = null
        ): ClientConfigProperty =
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
            baseClass: Symbol? = null
        ): ClientConfigProperty =
            builtInProperty(name, builtInSymbol("String", defaultValue), documentation, baseClass)
    }

    class Builder {
        var symbol: Symbol? = null
        // override the property name (defaults to symbol name)
        var name: String? = null
        var documentation: String? = null

        var baseClass: Symbol? = null

        fun build(): ClientConfigProperty = ClientConfigProperty(this)
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

private fun builtInProperty(name: String, symbol: Symbol, documentation: String?, baseClass: Symbol?): ClientConfigProperty =
    ClientConfigProperty {
        this.symbol = symbol
        this.name = name
        this.documentation = documentation
        this.baseClass = baseClass
    }

/**
 * Common client runtime related config properties
 */
object KotlinClientRuntimeConfigProperty {
    val HttpClientEngine: ClientConfigProperty
    val IdempotencyTokenProvider: ClientConfigProperty
    val SdkLogMode: ClientConfigProperty

    init {
        val httpClientConfigSymbol = buildSymbol {
            name = "HttpClientConfig"
            namespace(KotlinDependency.HTTP, "config")
        }

        HttpClientEngine = ClientConfigProperty {
            symbol = buildSymbol {
                name = "HttpClientEngine"
                namespace(KotlinDependency.HTTP, "engine")
            }
            baseClass = httpClientConfigSymbol
            documentation = """
            Override the default HTTP client configuration (e.g. configure proxy behavior, concurrency, etc)    
            """.trimIndent()
        }

        IdempotencyTokenProvider = ClientConfigProperty {
            symbol = buildSymbol {
                name = "IdempotencyTokenProvider"
                namespace(KotlinDependency.CORE, "config")
            }

            baseClass = buildSymbol {
                name = "IdempotencyTokenConfig"
                namespace(KotlinDependency.CORE, "config")
            }

            documentation = """
            Override the default idempotency token generator. SDK clients will generate tokens for members
            that represent idempotent tokens when not explicitly set by the caller using this generator.
            """.trimIndent()
        }

        SdkLogMode = ClientConfigProperty {
            symbol = buildSymbol {
                name = "SdkLogMode"
                namespace(KotlinDependency.CORE, "client")
                defaultValue = "SdkLogMode.Default"
                nullable = false
            }

            baseClass = buildSymbol {
                name = "SdkClientConfig"
                namespace(KotlinDependency.CORE, "config")
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
    }
}
