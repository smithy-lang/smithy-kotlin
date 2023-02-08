/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.util

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.model.boxed
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.model.defaultValue

typealias CustomPropertyRenderer = (ConfigProperty, KotlinWriter) -> Unit

/**
 * Represents a configuration property (e.g. service client config)
 *
 * e.g.
 *
 * ```kotlin
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

    fun toBuilder(): Builder = Builder().apply {
        symbol = this@ConfigProperty.symbol
        builderSymbol = this@ConfigProperty.builderSymbol.takeUnless { it == this@ConfigProperty.symbol }
        toBuilderExpression = this@ConfigProperty.toBuilderExpression
        name = this@ConfigProperty.propertyName
        documentation = this@ConfigProperty.documentation
        baseClass = this@ConfigProperty.baseClass
        builderBaseClass = this@ConfigProperty.builderBaseClass
        propertyType = this@ConfigProperty.propertyType
        additionalImports = this@ConfigProperty.additionalImports
        order = this@ConfigProperty.order
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

        /**
         * Convenience function to set the [builderBaseClass] symbol to a `Builder` class nested in the base
         * class interface itself (common in the runtime)
         */
        fun useNestedBuilderBaseClass() {
            val base = checkNotNull(baseClass) { "must set baseClass before calling" }
            builderBaseClass = buildSymbol {
                name = "${base.name}.Builder"
                namespace = base.namespace
            }
        }

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
