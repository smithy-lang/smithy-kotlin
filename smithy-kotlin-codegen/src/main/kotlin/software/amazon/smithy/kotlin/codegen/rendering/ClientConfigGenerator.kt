/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.kotlin.codegen.core.RenderingContext
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.model.expectTrait
import software.amazon.smithy.kotlin.codegen.model.hasIdempotentTokenMember
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.DefaultEndpointProviderGenerator
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.EndpointProviderGenerator
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.kotlin.codegen.utils.toCamelCase
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeType
import software.amazon.smithy.rulesengine.traits.ClientContextParamsTrait
import software.amazon.smithy.rulesengine.traits.EndpointRuleSetTrait

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
    vararg properties: ClientConfigProperty,
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
            }
            if (context.shape != null && context.shape.hasIdempotentTokenMember(context.model)) {
                defaultProps.add(KotlinClientRuntimeConfigProperty.IdempotencyTokenProvider)
            }
            defaultProps.add(KotlinClientRuntimeConfigProperty.RetryStrategy)

            if (context.shape != null && context.shape.hasTrait<ClientContextParamsTrait>()) {
                defaultProps.addAll(clientContextConfigProps(context.shape.expectTrait()))
            }

            defaultProps.add(
                ClientConfigProperty {
                    val hasRules = context.shape?.hasTrait<EndpointRuleSetTrait>() == true
                    symbol = EndpointProviderGenerator.getSymbol(context.settings)
                    propertyType = if (hasRules) { // if there's a ruleset, we have a usable default, otherwise caller has to provide their own
                        additionalImports = listOf(DefaultEndpointProviderGenerator.getSymbol(context.settings))
                        ClientConfigPropertyType.RequiredWithDefault("DefaultEndpointProvider()")
                    } else {
                        ClientConfigPropertyType.Required()
                    }
                    documentation = """
                        The endpoint provider used to determine where to make service requests.
                    """.trimIndent()
                },
            )

            return defaultProps
        }

        /**
         * Derives client config properties from the service context params trait.
         */
        fun clientContextConfigProps(trait: ClientContextParamsTrait): List<ClientConfigProperty> = buildList {
            trait.parameters.forEach { (k, v) ->
                add(
                    when (v.type) {
                        ShapeType.BOOLEAN -> ClientConfigProperty.Boolean(
                            name = k.toCamelCase(),
                            defaultValue = false,
                            documentation = v.documentation.getOrNull(),
                        )
                        ShapeType.STRING -> ClientConfigProperty.String(
                            name = k.toCamelCase(),
                            defaultValue = null,
                            documentation = v.documentation.getOrNull(),
                        )
                        else -> throw CodegenException("unsupported client context param type ${v.type}")
                    },
                )
            }
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

        props.sortWith(compareBy({ it.order }, { it.propertyName }))
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
                    builderReturnType,
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
                        prop.propertyType.message ?: "${prop.propertyName} is a required configuration property",
                    )
                }
                is ClientConfigPropertyType.RequiredWithDefault -> {
                    ctx.writer.write(
                        "$override val #1L: #2T = builder.#1L ?: #3L",
                        prop.propertyName,
                        prop.symbol,
                        prop.propertyType.default,
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
