/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package software.amazon.smithy.kotlin.codegen

import software.amazon.smithy.codegen.core.Symbol

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
    val propertyName: String = builder.name ?: symbol.name.decapitalize()

    /**
     * Additional base classes config should inherit from
     *
     * NOTE: Adding 1 or more base classes will implicitly render the property with an `override` modifier
     */
    val baseClasses: List<Symbol> = builder.baseClasses

    /**
     * Flag indicating if this property stems from some base class and needs an override modifier when rendered
     */
    internal val requiresOverride: Boolean
        get() = baseClasses.isNotEmpty()

    companion object {
        operator fun invoke(block: Builder.() -> Unit): ClientConfigProperty =
            Builder().apply(block).build()

        /**
         * Convenience init for an integer symbol.
         * @param name The property name
         * @param defaultValue The default value the config property should have if not set (if not specified the
         * parameter is assumed nullable)
         * @param documentation Help text to render as documentation for the property
         * @param baseClasses Additional base classes the config class should inherit from (assumes this property
         * stems from one or more base classes)
         */
        fun Integer(
            name: String,
            defaultValue: Int? = null,
            documentation: String? = null,
            vararg baseClasses: Symbol
        ): ClientConfigProperty =
            builtInProperty(name, builtInSymbol("Int", defaultValue?.toString()), documentation, *baseClasses)

        /**
         * Convenience init for a boolean symbol.
         * @param name The property name
         * @param defaultValue The default value the config property should have if not set (if not specified the
         * parameter is assumed nullable)
         * @param documentation Help text to render as documentation for the property
         * @param baseClasses Additional base classes the config class should inherit from (assumes this property
         * stems from one or more base classes)
         */
        fun Bool(
            name: String,
            defaultValue: Boolean? = null,
            documentation: String? = null,
            vararg baseClasses: Symbol
        ): ClientConfigProperty =
            builtInProperty(name, builtInSymbol("Boolean", defaultValue?.toString()), documentation, *baseClasses)

        /**
         * Convenience init for a string symbol.
         * @param name The property name
         * @param defaultValue The default value the config property should have if not set (if not specified the
         * parameter is assumed nullable)
         * @param documentation Help text to render as documentation for the property
         * @param baseClasses Additional base classes the config class should inherit from (assumes this property
         * stems from one or more base classes)
         */
        fun String(
            name: String,
            defaultValue: String? = null,
            documentation: String? = null,
            vararg baseClasses: Symbol
        ): ClientConfigProperty =
            builtInProperty(name, builtInSymbol("String", defaultValue), documentation, *baseClasses)
    }

    class Builder {
        var symbol: Symbol? = null
        // override the property name (defaults to symbol name)
        var name: String? = null
        var documentation: String? = null

        // additional base classes
        val baseClasses: MutableList<Symbol> = mutableListOf()

        /**
         * Add additional base classes to the config
         */
        fun addBaseClass(name: String) = addBaseClass(Symbol.builder().name(name).build())

        /**
         * Add additional base classes to the config
         */
        fun addBaseClass(symbol: Symbol) = baseClasses.add(symbol)

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

private fun builtInProperty(name: String, symbol: Symbol, documentation: String?, vararg baseClasses: Symbol): ClientConfigProperty =
    ClientConfigProperty {
        this.symbol = symbol
        this.name = name
        this.documentation = documentation
        baseClasses.forEach {
            addBaseClass(it)
        }
    }

/**
 * Common client runtime related config properties
 */
object KotlinClientRuntimeConfigProperty {
    val HttpClientEngine: ClientConfigProperty
    val HttpClientEngineConfig: ClientConfigProperty
    val IdempotencyTokenProvider: ClientConfigProperty

    init {
        val httpClientConfigSymbol = buildSymbol {
            name = "HttpClientConfig"
            namespace(KotlinDependency.CLIENT_RT_HTTP, "config")
        }

        HttpClientEngine = ClientConfigProperty {
            symbol = buildSymbol {
                name = "HttpClientEngine"
                namespace(KotlinDependency.CLIENT_RT_HTTP, "engine")
            }
            baseClasses += httpClientConfigSymbol
            documentation = """
            Override the default HTTP client configuration (e.g. configure proxy behavior, concurrency, etc)    
            """.trimIndent()
        }

        HttpClientEngineConfig = ClientConfigProperty {
            symbol = buildSymbol {
                name = "HttpClientEngineConfig"
                namespace(KotlinDependency.CLIENT_RT_HTTP, "engine")
            }
            baseClasses += httpClientConfigSymbol
            documentation = """
            Override the default HTTP client engine used for round tripping requests. This allow sharing a common
            HTTP engine between multiple clients, substituting with a different engine, etc.
            User is responsible for cleaning up the engine and any associated resources.
            """.trimIndent()
        }

        IdempotencyTokenProvider = ClientConfigProperty {
            symbol = buildSymbol {
                name = "IdempotencyTokenProvider"
                namespace(KotlinDependency.CLIENT_RT_CORE, "config")
            }

            baseClasses += buildSymbol {
                name = "IdempotencyTokenConfig"
                namespace(KotlinDependency.CLIENT_RT_CORE, "config")
            }

            documentation = """
            Override the default idempotency token generator. SDK clients will generate tokens for members
            that represent idempotent tokens when not explicitly set by the caller using this generator.
            """.trimIndent()
        }
    }
}
