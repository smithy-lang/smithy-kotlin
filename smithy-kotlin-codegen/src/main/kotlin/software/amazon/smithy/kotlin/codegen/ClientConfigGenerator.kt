/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolReference

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

/**
 * Represents a service client config property to be added to the generated client
 */
sealed class ConfigProperty {
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
    abstract val symbol: Symbol

    /**
     * Help text to be rendered for the property
     */
    abstract val documentation: kotlin.String?

    /**
     * Get the property name rendered to config
     */
    abstract val propertyName: kotlin.String

    /**
     * Additional base classes config should inherit from
     *
     * NOTE: Adding 1 or more base classes will implicitly render the property with an  `override` modifier
     */
    abstract val inheritFrom: List<kotlin.String>

    /**
     * Flag indicating if this property stems from some base class and needs an override modifier when rendered
     */
    internal val requiresOverride: Boolean
        get() = inheritFrom.isNotEmpty()

    /**
     * Convenience type for simple integer properties
     */
    data class Integer(
        /**
         * The name of the property to render on config
         */
        val name: kotlin.String,

        /**
         * The default value to assign to the property
         */
        val defaultValue: Int? = null,

        override val documentation: kotlin.String? = null,

        override val inheritFrom: List<kotlin.String> = listOf()
    ) : ConfigProperty() {
        override val symbol: Symbol = builtInSymbol("Int", defaultValue?.let { "$it" })
        override val propertyName: kotlin.String = name
    }

    /**
     * Convenience type for simple string properties
     */
    data class String(
        /**
         * The name of the property to render on config
         */
        val name: kotlin.String,

        /**
         * The default value to assign to the property
         */
        val defaultValue: kotlin.String? = null,
        override val documentation: kotlin.String? = null,
        override val inheritFrom: List<kotlin.String> = listOf()
    ) : ConfigProperty() {
        override val symbol: Symbol = builtInSymbol("String", defaultValue)
        override val propertyName: kotlin.String = name
    }

    /**
     * Convenience type for simple boolean properties
     */
    data class Bool(
        /**
         * The name of the property to render on config
         */
        val name: kotlin.String,

        /**
         * The default value to assign to the property
         */
        val defaultValue: Boolean? = null,

        override val documentation: kotlin.String? = null,
        override val inheritFrom: List<kotlin.String> = listOf()

    ) : ConfigProperty() {
        override val symbol: Symbol = builtInSymbol("Boolean", defaultValue?.let { "$it" })
        override val propertyName: kotlin.String = name
    }

    /**
     * Custom property type. Symbol can reference any custom user symbol.
     */
    class Custom(
        /**
         * Custom symbol to use as the property type
         *
         * NOTE: Dependencies and references will be processed for the symbol when rendering the config implementation
         */
        override val symbol: Symbol,

        /**
         * The name of the property to render on config (defaults to the symbol name with first letter lower-cased)
         */
        private val name: kotlin.String? = null,

        override val documentation: kotlin.String? = null,
        override val inheritFrom: List<kotlin.String> = listOf()

    ) : ConfigProperty() {
        override val propertyName: kotlin.String
            get() = name ?: symbol.name.decapitalize()
    }
}

/**
 * Common client runtime related config properties
 */
object SmithyConfigProperty {
    val HttpClientEngine: ConfigProperty
    val HttpClientEngineConfig: ConfigProperty
    val IdempotencyTokenProvider: ConfigProperty

    init {
        val httpClientConfigSymbol = Symbol.builder()
            .name("HttpClientConfig")
            .namespace(KotlinDependency.CLIENT_RT_HTTP, "config")
            .build()

        val httpClientEngineSymbol = Symbol.builder()
            .name("HttpClientEngine")
            .namespace(KotlinDependency.CLIENT_RT_HTTP, "engine")
            .boxed()
            .addReference(httpClientConfigSymbol, SymbolReference.ContextOption.USE)
            .build()

        val httpClientEngineConfigSymbol = Symbol.builder()
            .name("HttpClientEngineConfig")
            .namespace(KotlinDependency.CLIENT_RT_HTTP, "engine")
            .boxed()
            .addReference(httpClientConfigSymbol, SymbolReference.ContextOption.USE)
            .build()

        val idempotencyProviderConfig = Symbol.builder()
            .name("IdempotencyTokenConfig")
            .namespace(KotlinDependency.CLIENT_RT_CORE, "config")
            .build()

        val idempotencyProviderSymbol = Symbol.builder()
            .name("IdempotencyTokenProvider")
            .boxed()
            .namespace(KotlinDependency.CLIENT_RT_CORE, "config")
            .addReference(idempotencyProviderConfig, SymbolReference.ContextOption.USE)
            .build()

        HttpClientEngine = ConfigProperty.Custom(
            httpClientEngineSymbol,
            inheritFrom = listOf("HttpClientConfig"),
            documentation = """
            Override the default HTTP client configuration (e.g. configure proxy behavior, concurrency, etc)    
            """.trimIndent()
        )

        HttpClientEngineConfig = ConfigProperty.Custom(
            httpClientEngineConfigSymbol,
            inheritFrom = listOf("HttpClientConfig"),
            documentation = """
            Override the default HTTP client engine used for round tripping requests. This allow sharing a common
            HTTP engine between multiple clients, substituting with a different engine, etc.
            User is responsible for cleaning up the engine and any associated resources.
            """.trimIndent()
        )

        IdempotencyTokenProvider = ConfigProperty.Custom(
            idempotencyProviderSymbol,
            inheritFrom = listOf("IdempotencyTokenConfig"),
            documentation = """
            Override the default idempotency token generator. SDK clients will generate tokens for members
            that represent idempotent tokens when not explicitly set by the caller using this generator.
            """.trimIndent()
        )
    }
}

/**
 * Default generator for rendering a service config. By default integrations can register additional properties
 * without overriding the entirety of service config generation.
 *
 * @param ctx The rendering context to drive the generator
 * @param detectDefaultProps Flag indicating if properties should be added automatically based on model detection
 * @param properties Additional properties to register on the config interface
 */
class ClientConfigGenerator(
    private val ctx: RenderingContext,
    detectDefaultProps: Boolean = true,
    vararg properties: ConfigProperty
) {

    // TODO - allow extending from integrations or overridding by making this a base class
    private val props = mutableListOf<ConfigProperty>()

    init {
        props.addAll(properties)
        if (detectDefaultProps) {
            registerDefaultProps()
        }
    }

    /**
     * Attempt to detect and register properties automatically based on the model
     */
    private fun registerDefaultProps() {
        if (ctx.protocolGenerator?.applicationProtocol?.isHttpProtocol == true) {
            props.add(SmithyConfigProperty.HttpClientEngineConfig)
            props.add(SmithyConfigProperty.HttpClientEngine)
        }

        if (ctx.service.hasIdempotentTokenMember(ctx.model)) {
            props.add(SmithyConfigProperty.IdempotencyTokenProvider)
        }
    }

    fun render() {
        // push context to be used throughout generation of the class
        ctx.writer.putContext("configClass.name", "Config")

        addPropertyImports()

        props.sortBy { it.propertyName }
        val baseClasses = props.flatMap { it.inheritFrom }.toSet().joinToString(", ")
        val formattedBaseClasses = if (baseClasses.isNotEmpty()) ": $baseClasses" else ""
        ctx.writer.openBlock("class \$configClass.name:L private constructor(builder: BuilderImpl)$formattedBaseClasses {")
            .call { renderImmutableProperties() }
            .call { renderJavaBuilderInterface() }
            .call { renderDslBuilderInterface() }
            .call { renderBuilderImpl() }
            .closeBlock("}")

        ctx.writer.removeContext("configClass.name")
    }

    /**
     * register import statements from config properties
     */
    private fun addPropertyImports() {
        props.forEach {
            ctx.writer.addImportReferences(it.symbol, SymbolReference.ContextOption.USE)
            ctx.writer.addImport(it.symbol)
        }
    }

    private fun renderImmutableProperties() {
        props.forEach { prop ->
            val override = if (prop.requiresOverride) "override " else ""
            ctx.writer.write("${override}val \$1L: \$2T = builder.\$1L", prop.propertyName, prop.symbol)
        }
    }

    private fun renderJavaBuilderInterface() {
        ctx.writer.write("")
            .withBlock("interface Builder {", "}") {
                props.forEach { prop ->
                    // we want the type names sans nullability (?) for arguments
                    write("fun \$1L(\$1L: \$2L): Builder", prop.propertyName, prop.symbol.name)
                }
                write("fun build(): \$configClass.name:L")
            }
    }

    private fun renderDslBuilderInterface() {
        ctx.writer.write("")
            .withBlock("interface DslBuilder {", "}") {
                props.forEach { prop ->
                    prop.documentation?.let { ctx.writer.dokka(it) }
                    write("var \$L: \$T", prop.propertyName, prop.symbol)
                    write("")
                }
                write("")
                write("fun build(): \$configClass.name:L")
            }
    }

    private fun renderBuilderImpl() {
        ctx.writer.write("")
            .withBlock("internal class BuilderImpl() : Builder, DslBuilder {", "}") {
                // override DSL properties
                props.forEach { prop ->
                    write("override var \$L: \$D", prop.propertyName, prop.symbol)
                }
                write("")

                write("")
                write("override fun build(): \$configClass.name:L = \$configClass.name:L(this)")
                props.forEach { prop ->
                    // we want the type names sans nullability (?) for arguments
                    write("override fun \$1L(\$1L: \$2L): Builder = apply { this.\$1L = \$1L }", prop.propertyName, prop.symbol.name)
                }
            }
    }
}
