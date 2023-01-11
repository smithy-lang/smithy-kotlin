/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.util

import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.withBlock

/**
 * Re-usable base class for generating some type that only contains configuration.
 * e.g. roughly something shaped like below
 *
 * ```
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

    // TODO - add overrides for base class names/etc

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
        val baseClasses = props
            .mapNotNull { it.baseClass?.name }
            .sorted()
            .toSet()
            .joinToString(", ")

        val formattedBaseClasses = if (baseClasses.isNotEmpty()) ": $baseClasses" else ""
        writer.openBlock("public class #configClass.name:L private constructor(builder: Builder)$formattedBaseClasses {")
            .call { renderImmutableProperties(sortedProps, writer) }
            .call { renderCompanionObject(writer) }
            .call { renderBuilder(sortedProps, writer) }
            .closeBlock("}")

        writer.removeContext("configClass.name")
    }

    protected open fun renderCompanionObject(writer: KotlinWriter) {
        writer.withBlock("public companion object {", "}") {
            write("public inline operator fun invoke(block: Builder.() -> kotlin.Unit): #configClass.name:L = Builder().apply(block).build()")
        }
    }

    /**
     * register import statements from config properties
     */
    private fun addPropertyImports(props: Collection<ConfigProperty>, writer: KotlinWriter) {
        props.forEach {
            it.baseClass?.let { baseClass ->
                writer.addImport(baseClass)
            }
            writer.addImport(it.symbol)
            writer.addImportReferences(it.symbol, SymbolReference.ContextOption.USE)
            it.additionalImports.forEach { symbol ->
                writer.addImport(symbol)
            }
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

    protected open fun renderBuilder(props: Collection<ConfigProperty>, writer: KotlinWriter) {
        writer.write("")
            .withBlock("public class Builder {", "}") {
                // override DSL properties
                props
                    .filter { it.propertyType !is ConfigPropertyType.ConstantValue }
                    .forEach { prop ->
                        prop.documentation?.let { writer.dokka(it) }
                        write("public var #L: #D", prop.propertyName, prop.symbol)
                        write("")
                    }

                // TODO - make this configurable
                write("@PublishedApi")
                write("internal fun build(): #configClass.name:L = #configClass.name:L(this)")
            }
    }
}
