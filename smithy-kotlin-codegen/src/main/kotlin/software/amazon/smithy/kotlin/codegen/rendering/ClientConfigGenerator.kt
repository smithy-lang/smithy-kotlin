/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.kotlin.codegen.core.RenderingContext
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.model.hasIdempotentTokenMember
import software.amazon.smithy.model.shapes.ServiceShape

/**
 * Default generator for rendering a service config. By default integrations can register additional properties
 * without overriding the entirety of service config generation.
 *
 * @param ctx The rendering context to drive the generator
 * @param detectDefaultProps Flag indicating if properties should be added automatically based on model detection
 * @param properties Additional properties to register on the config interface
 */
class ClientConfigGenerator(
    private val ctx: RenderingContext<ServiceShape>,
    detectDefaultProps: Boolean = true,
    private val builderReturnType: Symbol? = null,
    vararg properties: ClientConfigProperty
) {

    private val props = mutableListOf<ClientConfigProperty>()

    init {
        props.addAll(properties)
        if (detectDefaultProps) {
            registerDefaultProps()
        }

        // register properties from integrations
        val integrationProps = ctx.integrations.flatMap { it.additionalServiceConfigProps(ctx) }
        props.addAll(integrationProps)
    }

    /**
     * Attempt to detect and register properties automatically based on the model
     */
    private fun registerDefaultProps() {
        props.add(KotlinClientRuntimeConfigProperty.SdkLogMode)
        if (ctx.protocolGenerator?.applicationProtocol?.isHttpProtocol == true) {
            props.add(KotlinClientRuntimeConfigProperty.HttpClientEngine)
        }
        if (ctx.shape != null && ctx.shape.hasIdempotentTokenMember(ctx.model)) {
            props.add(KotlinClientRuntimeConfigProperty.IdempotencyTokenProvider)
        }
        props.add(KotlinClientRuntimeConfigProperty.RetryStrategy)
    }

    fun render() {
        if (ctx.writer.getContext("configClass.name") == null) {
            // push context to be used throughout generation of the class
            ctx.writer.putContext("configClass.name", "Config")
        }

        addPropertyImports()

        props.sortBy { it.propertyName }
        val baseClasses = props
            .mapNotNull { it.baseClass?.name }
            .toSet()
            .joinToString(", ")

        val formattedBaseClasses = if (baseClasses.isNotEmpty()) ": $baseClasses" else ""
        ctx.writer.openBlock("class #configClass.name:L private constructor(builder: BuilderImpl)$formattedBaseClasses {")
            .call { renderImmutableProperties() }
            .call { renderCompanionObject() }
            .call { renderJavaBuilderInterface() }
            .call { renderDslBuilderInterface() }
            .call { renderBuilderImpl() }
            .closeBlock("}")

        ctx.writer.removeContext("configClass.name")
    }

    private fun renderCompanionObject() {
        if (builderReturnType != null) {
            ctx.writer.withBlock("companion object {", "}") {
                write("@JvmStatic")
                write("fun fluentBuilder(): FluentBuilder = BuilderImpl()")
                write("fun builder(): DslBuilder = BuilderImpl()")
                write("operator fun invoke(block: DslBuilder.() -> kotlin.Unit): #T = BuilderImpl().apply(block).build()", builderReturnType)
            }
        }
    }

    /**
     * register import statements from config properties
     */
    private fun addPropertyImports() {
        props.forEach {
            it.baseClass?.let { baseClass ->
                ctx.writer.addImport(baseClass)
            }
            ctx.writer.addImport(it.symbol)
            ctx.writer.addImportReferences(it.symbol, SymbolReference.ContextOption.USE)
        }
    }

    private fun renderImmutableProperties() {
        props.forEach { prop ->
            val override = if (prop.requiresOverride) "override " else ""

            when {
                prop.constantValue != null -> {
                    ctx.writer.write("${override}val #1L: #2T = #3L", prop.propertyName, prop.symbol, prop.constantValue)
                }
                prop.required -> {
                    ctx.writer.write("${override}val #1L: #2T = builder.#1L ?: throw ClientException(\"#1L must be set\")", prop.propertyName, prop.symbol)
                }
                else -> {
                    ctx.writer.write("${override}val #1L: #2P = builder.#1L", prop.propertyName, prop.symbol)
                }
            }
        }
    }

    private fun renderJavaBuilderInterface() {
        ctx.writer.write("")
            .withBlock("interface FluentBuilder {", "}") {
                props
                    .filter { it.constantValue == null }
                    .forEach { prop ->
                        // we want the type names sans nullability (?) for arguments
                        write("fun #1L(#1L: #2L): FluentBuilder", prop.propertyName, prop.symbol.name)
                    }
                write("fun build(): #configClass.name:L")
            }
    }

    private fun renderDslBuilderInterface() {
        ctx.writer.write("")
            .withBlock("interface DslBuilder {", "}") {
                props
                    .filter { it.constantValue == null }
                    .forEach { prop ->
                        prop.documentation?.let { ctx.writer.dokka(it) }
                        write("var #L: #P", prop.propertyName, prop.symbol)
                        write("")
                    }
                write("")
                write("fun build(): #configClass.name:L")
            }
    }

    private fun renderBuilderImpl() {
        ctx.writer.write("")
            .withBlock("internal class BuilderImpl() : FluentBuilder, DslBuilder {", "}") {
                // override DSL properties
                props
                    .filter { it.constantValue == null }
                    .forEach { prop ->
                        write("override var #L: #D", prop.propertyName, prop.symbol)
                    }
                write("")

                write("")
                write("override fun build(): #configClass.name:L = #configClass.name:L(this)")
                props
                    .filter { it.constantValue == null }
                    .forEach { prop ->
                        // we want the type names sans nullability (?) for arguments
                        write("override fun #1L(#1L: #2L): FluentBuilder = apply { this.#1L = #1L }", prop.propertyName, prop.symbol.name)
                    }
            }
    }
}
