/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.endpoints

import aws.smithy.kotlin.codegen.KotlinSettings
import aws.smithy.kotlin.codegen.core.GenerationContext
import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.core.RuntimeTypes
import aws.smithy.kotlin.codegen.core.SymbolRenderer
import aws.smithy.kotlin.codegen.core.closeAndOpenBlock
import aws.smithy.kotlin.codegen.core.declareSection
import aws.smithy.kotlin.codegen.core.defaultName
import aws.smithy.kotlin.codegen.core.withBlock
import aws.smithy.kotlin.codegen.integration.SectionId
import aws.smithy.kotlin.codegen.model.buildSymbol
import aws.smithy.kotlin.codegen.model.defaultName
import aws.smithy.kotlin.codegen.model.format
import aws.smithy.kotlin.codegen.model.getEndpointRules
import aws.smithy.kotlin.codegen.model.getTrait
import aws.smithy.kotlin.codegen.model.knowledge.EndpointParameterIndex
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import aws.smithy.kotlin.codegen.rendering.waiters.KotlinJmespathExpressionVisitor
import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.jmespath.JmespathExpression
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.rulesengine.language.syntax.parameters.Parameter
import software.amazon.smithy.rulesengine.language.syntax.parameters.ParameterType
import software.amazon.smithy.rulesengine.traits.ClientContextParamsTrait
import software.amazon.smithy.rulesengine.traits.OperationContextParamDefinition
import software.amazon.smithy.rulesengine.traits.StaticContextParamDefinition
import software.amazon.smithy.utils.StringUtils

object EndpointBusinessMetrics : SectionId

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
        val operationsWithContextBindings = operations.filter { epParameterIndex.hasContextParams(it) }

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
                operationsWithContextBindings.forEach { op ->
                    write("#S to ::#L,", op.id.name, op.bindEndpointContextFnName())
                }
            }

        operationsWithContextBindings.forEach { op ->
            renderBindOperationContextFunction(op, epParameterIndex)
        }
    }

    private fun renderBindOperationContextFunction(op: OperationShape, epParameterIndex: EndpointParameterIndex) =
        writer.write("")
            .withBlock(
                "private fun #L(builder: #T.Builder, request: #T): Unit {",
                "}",
                op.bindEndpointContextFnName(),
                EndpointParametersGenerator.getSymbol(ctx.settings),
                RuntimeTypes.HttpClient.Operation.ResolveEndpointRequest,
            ) {
                renderBindOperationContextParams(epParameterIndex, op)
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
                /*
                The spec dictates a specific source order to use when binding parameters (from most specific to least):

                1. staticContextParams (from operation shape)
                2. contextParam (from member of operation input shape)
                3. operationContextParams (from operation shape)
                4. clientContextParams (from service shape)
                5. builtin binding
                6. builtin default

                Sources 5 and 6 are SDK-specific

                Builtin bindings are plugged in and rendered beforehand such that any bindings from source 1, 2, or 3
                can supersede them.
                 */

                // Render builtins
                if (rules != null) {
                    ctx.integrations
                        .mapNotNull { it.customizeEndpointResolution(ctx) }
                        .forEach {
                            it.renderBindEndpointBuiltins(ctx, rules, writer)
                        }
                }

                // Render client context
                renderBindClientContextParams(ctx, writer)

                // Render operation static/input/operation context (if any)
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

            declareSection(EndpointBusinessMetrics)

            renderPostResolution()
            write("return endpoint")
        }
    }

    private fun renderBindOperationContextParams(
        epParameterIndex: EndpointParameterIndex,
        op: OperationShape,
    ) {
        if (rules == null) return

        val staticContextParams = epParameterIndex.staticContextParams(op)
        val inputContextParams = epParameterIndex.inputContextParams(op)
        val operationContextParams = epParameterIndex.operationContextParams(op)

        if (inputContextParams.isNotEmpty()) renderInput(op)

        for (param in rules.parameters.toList()) {
            val paramName = param.name.toString()
            val paramDefaultName = param.defaultName()

            // Check static params
            val staticParam = staticContextParams?.parameters?.get(paramName)
            if (staticParam != null) {
                renderStaticParam(staticParam, paramDefaultName, param)
                continue
            }

            // Check input params
            val inputParam = inputContextParams[paramName]
            if (inputParam != null) {
                renderInputParam(inputParam, paramDefaultName)
                continue
            }

            // Check operation params
            val operationParam = operationContextParams?.get(paramName)
            if (operationParam != null) {
                renderOperationParam(operationParam, paramDefaultName, op, inputContextParams)
            }
        }
    }

    private fun renderInput(op: OperationShape) {
        writer.addImport(RuntimeTypes.Core.Collections.get)
        writer.write("@Suppress(#S)", "UNCHECKED_CAST")
        val opInputShape = ctx.model.expectShape(op.inputShape)
        val inputSymbol = ctx.symbolProvider.toSymbol(opInputShape)
        writer.write("val input = request.context[#T.OperationInput] as #T", RuntimeTypes.HttpClient.Operation.HttpOperationContext, inputSymbol)
    }

    private fun renderStaticParam(staticParam: StaticContextParamDefinition, paramDefaultName: String, param: Parameter) {
        writer.writeInline("builder.#L = ", paramDefaultName)
        when (param.type) {
            ParameterType.STRING -> writer.write("#S", staticParam.value.expectStringNode().value)
            ParameterType.BOOLEAN -> writer.write("#L", staticParam.value.expectBooleanNode().value)
            ParameterType.STRING_ARRAY -> writer.write("#L", staticParam.value.expectArrayNode().elements.format())
            else -> throw CodegenException("unexpected static context param type ${param.type}")
        }
    }

    private fun renderInputParam(inputParam: MemberShape, paramDefaultName: String) {
        writer.write("builder.#L = input.#L", paramDefaultName, inputParam.defaultName())
    }

    private fun renderOperationParam(operationParam: OperationContextParamDefinition, paramDefaultName: String, op: OperationShape, inputContextParams: Map<String, MemberShape>) {
        val opInputShape = ctx.model.expectShape(op.inputShape)

        if (inputContextParams.isEmpty()) {
            // This will already be rendered in the block if inputContextParams is not empty
            renderInput(op)
        }

        val jmespathVisitor = KotlinJmespathExpressionVisitor(
            GenerationContext(
                ctx.model,
                ctx.symbolProvider,
                ctx.settings,
            ),
            writer,
            opInputShape,
            "input", // reference the operation input during jmespath codegen
        )
        val expression = JmespathExpression.parse(operationParam.path)
        val expressionResult = expression.accept(jmespathVisitor)

        writer.write("builder.#L = #L", paramDefaultName, expressionResult.identifier)
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
