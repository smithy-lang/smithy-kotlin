/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.util

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.model.PropertyTypeMutability
import software.amazon.smithy.kotlin.codegen.model.propertyTypeMutability

/**
 * Reusable base class for generating some type that only contains configuration.
 * e.g. roughly something shaped like below
 *
 * ```kotlin
 * class MyConfig internal constructor(builder: Builder) {
 *     val foo = builder.foo
 *     val bar = builder.bar ?: DefaultBar()
 *
 *     class Builder {
 *         var foo: Foo? = null
 *         var bar: Boo? = null
 *     }
 *
 * }
 * ```
 */
abstract class AbstractConfigGenerator {

    /**
     * The generated type visibility e.g. `public`, `internal`, `private`.
     */
    open val visibility: String = "public"

    open fun render(
        ctx: CodegenContext,
        props: Collection<ConfigProperty>,
        writer: KotlinWriter,
    ) {
        if (writer.getContext("configClass.name") == null) {
            // push context to be used throughout generation of the class
            writer.putContext("configClass.name", "Config")
        }

        addPropertyImports(props, writer)

        val sortedProps = props.sortedWith(compareBy({ it.order }, { it.propertyName }))
        val formattedBaseClasses = props.formattedBaseClasses { it.baseClass to it.baseClassDelegate }

        writer.withBlock(
            "$visibility class #configClass.name:L private constructor(builder: Builder)$formattedBaseClasses {",
            "}",
        ) {
            renderImmutableProperties(sortedProps, writer)
            renderCompanionObject(writer)
            renderToBuilder(sortedProps, writer)
            renderBuilder(sortedProps, writer)
            renderAdditionalMethods(ctx, sortedProps, writer)
        }

        writer.removeContext("configClass.name")
    }

    /**
     * Hook to render additional methods on the generated type
     */
    protected open fun renderAdditionalMethods(ctx: CodegenContext, props: List<ConfigProperty>, writer: KotlinWriter) {
    }

    protected open fun renderCompanionObject(writer: KotlinWriter) {
        writer.withBlock("$visibility companion object {", "}") {
            write("$visibility inline operator fun invoke(block: Builder.() -> kotlin.Unit): #configClass.name:L = Builder().apply(block).build()")
        }
    }

    /**
     * register import statements from config properties
     */
    private fun addPropertyImports(props: Collection<ConfigProperty>, writer: KotlinWriter) {
        props.forEach {
            it.baseClass?.let(writer::addImport)
            it.baseClassDelegate?.symbol?.let(writer::addImport)
            it.builderBaseClassDelegate?.symbol?.let(writer::addImport)
            writer.addImport(it.symbol)
            writer.addImportReferences(it.symbol, SymbolReference.ContextOption.USE)
            it.additionalImports.forEach(writer::addImport)
        }
    }

    protected open fun renderImmutableProperties(props: Collection<ConfigProperty>, writer: KotlinWriter) {
        props.forEach { prop ->
            val override = if (prop.requiresOverride) "override" else "public"

            when (prop.propertyType) {
                is ConfigPropertyType.SymbolDefault -> {
                    writer.write("$override val #1L: #2P = builder.#1L", prop.propertyName, prop.symbol)
                }
                is ConfigPropertyType.ConstantValue -> {
                    writer.write("$override val #1L: #2T = #3L", prop.propertyName, prop.symbol, prop.propertyType.value)
                }
                is ConfigPropertyType.Required -> {
                    writer.write(
                        "$override val #1L: #2T = requireNotNull(builder.#1L) { #3S }",
                        prop.propertyName,
                        prop.symbol,
                        prop.propertyType.message ?: "${prop.propertyName} is a required configuration property",
                    )
                }
                is ConfigPropertyType.RequiredWithDefault -> {
                    writer.write(
                        "$override val #1L: #2T = builder.#1L ?: #3L",
                        prop.propertyName,
                        prop.symbol,
                        prop.propertyType.default,
                    )
                }
                is ConfigPropertyType.Custom -> prop.propertyType.render(prop, writer)
            }
        }
    }

    protected open fun renderToBuilder(props: Collection<ConfigProperty>, writer: KotlinWriter) {
        writer.write("")
            .withBlock("$visibility fun toBuilder(): Builder = Builder().apply {", "}") {
                props
                    .filter { it.propertyType !is ConfigPropertyType.ConstantValue }
                    .forEach { prop ->
                        write("#1L = this@#configClass.name:L.#1L#2L", prop.propertyName, prop.toBuilderExpression)
                    }
            }
    }

    protected open fun renderBuilder(props: Collection<ConfigProperty>, writer: KotlinWriter) {
        val formattedBaseClasses = props.formattedBaseClasses { it.builderBaseClass to it.builderBaseClassDelegate }

        writer.write("")
            .withBlock("$visibility class Builder$formattedBaseClasses {", "}") {
                // override DSL properties
                props
                    .filter { it.propertyType !is ConfigPropertyType.ConstantValue }
                    .forEach { prop ->

                        if (prop.propertyType is ConfigPropertyType.Custom && prop.propertyType.renderBuilder != null) {
                            val renderBuilderProp = checkNotNull(prop.propertyType.renderBuilder)
                            renderBuilderProp(prop, writer)
                        } else {
                            val override = if (prop.builderRequiresOverride) "override" else "public"
                            prop.documentation?.let { writer.dokka(it) }
                            val mutability = prop.builderSymbol.propertyTypeMutability ?: PropertyTypeMutability.MUTABLE
                            write("$override $mutability #L: #D", prop.propertyName, prop.builderSymbol)
                            write("")
                        }
                    }

                renderBuilderBuildMethod(writer)
            }
    }

    /**
     * Return the formatted base classes for the config property
     * @param transform the selector from config property that maps the property to a base class name
     */
    private inline fun Collection<ConfigProperty>.formattedBaseClasses(
        transform: (ConfigProperty) -> Pair<Symbol?, Delegate?>,
    ): String {
        val baseClasses = map(transform)
            .mapNotNull { (symbol, delegate) -> symbol?.format(delegate) }
            .sorted()
            .toSet()
            .joinToString(", ")

        return if (baseClasses.isNotEmpty()) " : $baseClasses" else ""
    }

    /**
     * Render the `build()` function for the builder
     */
    protected open fun renderBuilderBuildMethod(writer: KotlinWriter) {
        writer.apply {
            write("@PublishedApi")
            write("internal fun build(): #configClass.name:L = #configClass.name:L(this)")
        }
    }
}

private fun Symbol.format(delegate: Delegate?): String =
    name + (delegate?.let { " by ${it.delegationExpression}" } ?: "")
