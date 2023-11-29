/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.endpoints

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.model.*
import software.amazon.smithy.kotlin.codegen.model.knowledge.EndpointParameterIndex
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.rulesengine.language.syntax.parameters.ParameterType
import software.amazon.smithy.rulesengine.traits.ClientContextParamsTrait
import software.amazon.smithy.utils.StringUtils

/**
 * Generates resolver adapter for going from generic HTTP operation endpoint resolver to the generated
 * type specific provider generated based on the rules.
 */
class EndpointResolverAdapterGenerator(
    private val ctx: ProtocolGenerator.GenerationContext,
    private val writer: KotlinWriter,
    private val renderPostResolution: () -> Unit = {},
) {
    private val rules = ctx.service.getEndpointRules()

    companion object {
        const val CLASS_NAME = "EndpointResolverAdapter"

        fun getSymbol(settings: KotlinSettings): Symbol =
            buildSymbol {
                name = CLASS_NAME
                namespace = "${settings.pkg.name}.endpoints.internal"
                definitionFile = "$name.kt"
            }

        fun getResolveEndpointParamsFn(settings: KotlinSettings): Symbol =
            buildSymbol {
                name = "resolveEndpointParams"
                namespace = "${settings.pkg.name}.endpoints.internal"
            }
    }

    fun render() {
        writer.openBlock("internal class #L(", CLASS_NAME)
            .call {
                renderConstructorParams()
            }
            .closeAndOpenBlock("): #T {", RuntimeTypes.HttpClient.Operation.EndpointResolver)
            .write("")
            .write("")
            .call {
                renderResolve()
            }
            .closeBlock("}")
            .write("")
            .call {
                renderResolveEndpointParams()
                // render a single version shared across instances
                renderOperationContextBindingMap()
            }
    }

    private fun renderConstructorParams() {
        val service = ctx.symbolProvider.toSymbol(ctx.service)
        writer.write("private val config: #T.Config", service)
    }

    private fun renderOperationContextBindingMap() {
        val topDownIndex = TopDownIndex.of(ctx.model)
        val operations = topDownIndex.getContainedOperations(ctx.service)
        val epParameterIndex = EndpointParameterIndex.of(ctx.model)

        writer.write(
            "private typealias BindOperationContextParamsFn = (#T.Builder, #T) -> Unit",
            EndpointParametersGenerator.getSymbol(ctx.settings),
            RuntimeTypes.HttpClient.Operation.ResolveEndpointRequest,
        )
            .write("")
            .withBlock(
                "private val opContextBindings = mapOf<String, BindOperationContextParamsFn> (",
                ")",
            ) {
                val operationsWithContextBindings = operations.filter { epParameterIndex.hasContextParams(it) }
                operationsWithContextBindings.forEach { op ->
                    val bindFn = op.bindEndpointContextFn(ctx.settings) { fnWriter ->
                        fnWriter.withBlock(
                            "private fun #L(builder: #T.Builder, request: #T): Unit {",
                            "}",
                            op.bindEndpointContextFnName(),
                            EndpointParametersGenerator.getSymbol(ctx.settings),
                            RuntimeTypes.HttpClient.Operation.ResolveEndpointRequest,
                        ) {
                            renderBindOperationContextParams(epParameterIndex, op, fnWriter)
                        }
                    }
                    write("#S to ::#T,", op.id.name, bindFn)
                }
            }
    }

    private fun renderResolveEndpointParams() {
        // NOTE: this is internal as it's re-used for auth scheme resolver generators in specific instances where they
        // fallback to endpoint rules (e.g. S3 & EventBridge)
        writer.withBlock(
            "internal fun #T(config: #T.Config, request: #T): #T {",
            "}",
            getResolveEndpointParamsFn(ctx.settings),
            ctx.symbolProvider.toSymbol(ctx.service),
            RuntimeTypes.HttpClient.Operation.ResolveEndpointRequest,
            EndpointParametersGenerator.getSymbol(ctx.settings),
        ) {
            writer.addImport(RuntimeTypes.Core.Collections.get)
            withBlock("return #T {", "}", EndpointParametersGenerator.getSymbol(ctx.settings)) {
                // The SEP dictates a specific source order to use when binding parameters (from most specific to least):
                // 1. staticContextParams (from operation shape)
                // 2. contextParam (from member of operation input shape)
                // 3. clientContextParams (from service shape)
                // 4. builtin binding
                // 5. builtin default
                // Sources 4 and 5 are SDK-specific, builtin bindings are plugged in and rendered beforehand such that any bindings
                // from source 1 or 2 can supersede them.

                // Render builtins
                if (rules != null) {
                    ctx.integrations.forEach { it.renderBindEndpointBuiltins(ctx, rules, writer) }
                }

                // Render client context
                renderBindClientContextParams(ctx, writer)

                // Render operation static/input context (if any)
                write("val opName = request.context[#T.OperationName]", RuntimeTypes.SmithyClient.SdkClientOption)
                write("opContextBindings[opName]?.invoke(this, request)")
            }
        }
    }

    private fun renderResolve() {
        writer.withBlock(
            "override suspend fun resolve(request: #T): #T {",
            "}",
            RuntimeTypes.HttpClient.Operation.ResolveEndpointRequest,
            RuntimeTypes.SmithyClient.Endpoints.Endpoint,
        ) {
            write("val params = resolveEndpointParams(config, request)")
            write("val endpoint = config.endpointProvider.resolveEndpoint(params)")
            renderPostResolution()
            write("return endpoint")
        }
    }

    private fun renderBindOperationContextParams(
        epParameterIndex: EndpointParameterIndex,
        op: OperationShape,
        writer: KotlinWriter,
    ) {
        if (rules == null) return
        val staticContextParams = epParameterIndex.staticContextParams(op)
        val inputContextParams = epParameterIndex.inputContextParams(op)

        if (inputContextParams.isNotEmpty()) {
            writer.addImport(RuntimeTypes.Core.Collections.get)
            writer.write("@Suppress(#S)", "UNCHECKED_CAST")
            val opInputShape = ctx.model.expectShape(op.inputShape)
            val inputSymbol = ctx.symbolProvider.toSymbol(opInputShape)
            writer.write("val input = request.context[#T.OperationInput] as #T", RuntimeTypes.HttpClient.Operation.HttpOperationContext, inputSymbol)
        }

        for (param in rules.parameters.toList()) {
            val paramName = param.name.toString()
            val paramDefaultName = param.defaultName()

            val staticParam = staticContextParams?.parameters?.get(paramName)

            if (staticParam != null) {
                writer.writeInline("builder.#L = ", paramDefaultName)
                when (param.type) {
                    ParameterType.STRING -> writer.write("#S", staticParam.value.expectStringNode().value)
                    ParameterType.BOOLEAN -> writer.write("#L", staticParam.value.expectBooleanNode().value)
                    else -> throw CodegenException("unexpected static context param type ${param.type}")
                }
                continue
            }

            inputContextParams[paramName]?.let {
                writer.write("builder.#L = input.#L", paramDefaultName, it.defaultName())
            }
        }
    }

    private fun renderBindClientContextParams(ctx: ProtocolGenerator.GenerationContext, writer: KotlinWriter) {
        val clientContextParams = ctx.service.getTrait<ClientContextParamsTrait>() ?: return
        if (rules == null) return

        rules.parameters.toList().forEach { param ->
            val paramName = param.name.toString()
            val paramDefaultName = param.defaultName()
            clientContextParams.parameters?.get(paramName)?.let {
                writer.write("#1L = config.#1L", paramDefaultName)
            }
        }
    }
}

/**
 * Get the name of the function responsible for binding an operation's context parameters to endpoint parameters.
 */
fun OperationShape.bindEndpointContextFnName(): String = "bind" + StringUtils.capitalize(this.id.name) + "EndpointContext"

/**
 * Get the function responsible for binding an operation's context parameters to endpoint parameters and register [block]
 * which will be invoked to actually render the function (signature and implementation)
 */
fun OperationShape.bindEndpointContextFn(
    settings: KotlinSettings,
    block: SymbolRenderer,
): Symbol = buildSymbol {
    name = bindEndpointContextFnName()
    val adapterSymbol = EndpointResolverAdapterGenerator.getSymbol(settings)
    namespace = adapterSymbol.namespace
    // place body serializer in same file as endpoint resolver adapter
    definitionFile = adapterSymbol.definitionFile
    renderBy = block
}
