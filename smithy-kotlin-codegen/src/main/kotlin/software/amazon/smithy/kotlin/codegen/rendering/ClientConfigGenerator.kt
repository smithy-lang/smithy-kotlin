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

    companion object {
        /**
         * Attempt to detect configuration properties automatically based on the model
         */
        fun detectDefaultProps(context: RenderingContext<ServiceShape>): List<ClientConfigProperty> {
            val defaultProps = mutableListOf<ClientConfigProperty>()
            defaultProps.add(KotlinClientRuntimeConfigProperty.SdkLogMode)
            if (context.protocolGenerator?.applicationProtocol?.isHttpProtocol == true) {
                defaultProps.add(KotlinClientRuntimeConfigProperty.HttpClientEngine)
                defaultProps.add(KotlinClientRuntimeConfigProperty.EndpointResolver)
            }
            if (context.shape != null && context.shape.hasIdempotentTokenMember(context.model)) {
                defaultProps.add(KotlinClientRuntimeConfigProperty.IdempotencyTokenProvider)
            }
            defaultProps.add(KotlinClientRuntimeConfigProperty.RetryStrategy)
            return defaultProps
        }
    }

    private val props = mutableListOf<ClientConfigProperty>()

    init {
        props.addAll(properties)
        if (detectDefaultProps) {
            // register auto detected properties
            props.addAll(detectDefaultProps(ctx))
        }

        // register properties from integrations
        val integrationProps = ctx.integrations.flatMap { it.additionalServiceConfigProps(ctx) }
        props.addAll(integrationProps)
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
        ctx.writer.openBlock("public class #configClass.name:L private constructor(builder: Builder)$formattedBaseClasses {")
            .call { renderImmutableProperties() }
            .call { renderCompanionObject() }
            .call { renderBuilder() }
            .closeBlock("}")

        ctx.writer.removeContext("configClass.name")
    }

    private fun renderCompanionObject() {
        ctx.writer.withBlock("public companion object {", "}") {
            if (builderReturnType != null) {
                write(
                    "public inline operator fun invoke(block: Builder.() -> kotlin.Unit): #T = Builder().apply(block).build()",
                    builderReturnType
                )
            } else {
                write("public inline operator fun invoke(block: Builder.() -> kotlin.Unit): #configClass.name:L = Builder().apply(block).build()")
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
            it.additionalImports.forEach { symbol ->
                ctx.writer.addImport(symbol)
            }
        }
    }

    private fun renderImmutableProperties() {
        props.forEach { prop ->
            val override = if (prop.requiresOverride) "override" else "public"

            when (prop.propertyType) {
                is ClientConfigPropertyType.SymbolDefault -> {
                    ctx.writer.write("$override val #1L: #2P = builder.#1L", prop.propertyName, prop.symbol)
                }
                is ClientConfigPropertyType.ConstantValue -> {
                    ctx.writer.write("$override val #1L: #2T = #3L", prop.propertyName, prop.symbol, prop.propertyType.value)
                }
                is ClientConfigPropertyType.Required -> {
                    ctx.writer.write(
                        "$override val #1L: #2T = requireNotNull(builder.#1L) { #3S }",
                        prop.propertyName,
                        prop.symbol,
                        prop.propertyType.message ?: "${prop.propertyName} is a required configuration property"
                    )
                }
                is ClientConfigPropertyType.RequiredWithDefault -> {
                    ctx.writer.write(
                        "$override val #1L: #2T = builder.#1L ?: #3L",
                        prop.propertyName,
                        prop.symbol,
                        prop.propertyType.default
                    )
                }
                is ClientConfigPropertyType.Custom -> prop.propertyType.render(prop, ctx.writer)
            }
        }
    }

    private fun renderBuilder() {
        ctx.writer.write("")
            .withBlock("public class Builder {", "}") {
                // override DSL properties
                props
                    .filter { it.propertyType !is ClientConfigPropertyType.ConstantValue }
                    .forEach { prop ->
                        prop.documentation?.let { ctx.writer.dokka(it) }
                        write("public var #L: #D", prop.propertyName, prop.symbol)
                    }
                write("")

                write("@PublishedApi")
                write("internal fun build(): #configClass.name:L = #configClass.name:L(this)")
            }
    }
}
