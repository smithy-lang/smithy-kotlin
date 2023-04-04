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
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.OperationIndex
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.rulesengine.language.EndpointRuleSet
import software.amazon.smithy.rulesengine.language.syntax.parameters.Parameter
import software.amazon.smithy.rulesengine.language.syntax.parameters.ParameterType
import software.amazon.smithy.rulesengine.traits.ClientContextParamsTrait
import software.amazon.smithy.rulesengine.traits.ContextParamTrait
import software.amazon.smithy.rulesengine.traits.StaticContextParamsTrait

// TODO Add tests for bindings and builtins

/**
 * Generates resolver adapter for going from generic HTTP operation endpoint resolver to the generated
 * type specific provider generated based on the rules.
 * The binding of parameter values is generated separately in [EndpointParameterBindingGenerator], and that generated
 * implementation is plugged in here.
 */
class EndpointResolverAdapterGenerator(
    private val ctx: ProtocolGenerator.GenerationContext,
    private val writer: KotlinWriter,
    private val renderPostResolution: () -> Unit = {},
) {
    companion object {
        const val CLASS_NAME = "EndpointResolverAdapter"

        fun getSymbol(settings: KotlinSettings): Symbol =
            buildSymbol {
                name = CLASS_NAME
                namespace = "${settings.pkg.name}.endpoints.internal"
            }
    }

    fun render() {
        writer.openBlock("internal class #L<I>(", CLASS_NAME)
        renderConstructorParams()
        writer.closeAndOpenBlock("): #T {", RuntimeTypes.HttpClient.Operation.EndpointResolver)
        writer.write("")
        renderBody()
        writer.closeBlock("}")
    }

    private fun renderConstructorParams() {
        writer.write("private val endpointProvider: #T,", EndpointProviderGenerator.getSymbol(ctx.settings))
        writer.write("private val bindParams: #T.Builder.(input: I) -> Unit,", EndpointParametersGenerator.getSymbol(ctx.settings))
    }
    private fun renderBody() {
        writer.withBlock(
            "override suspend fun resolve(request: #T): #T {",
            "}",
            RuntimeTypes.HttpClient.Operation.ResolveEndpointRequest,
            RuntimeTypes.SmithyClient.Endpoints.Endpoint
        ){
            writer.addImport(RuntimeTypes.Core.Utils.get)
            write("@Suppress(#S)", "UNCHECKED_CAST")
            write("val input = request.context[#T.OperationInput] as I", RuntimeTypes.HttpClient.Operation.HttpOperationContext)
            withBlock("val params = #T {", "}", EndpointParametersGenerator.getSymbol(ctx.settings)) {
                write("bindParams(input)")
            }
            write("val endpoint = endpointProvider.resolveEndpoint(params)")
            renderPostResolution()
            write("return endpoint")
        }
    }
}

/**
 * Renders an endpoint parameter builder lambda that binds values for a specific operation.
 */
class EndpointParameterBindingGenerator(
    private val model: Model,
    private val service: ServiceShape,
    private val writer: KotlinWriter,
    private val op: OperationShape,
    private val rules: EndpointRuleSet,
    private val inputPrefix: String,
) {
    private val opIndex = OperationIndex.of(model)
    private val staticContextParams = op.getTrait<StaticContextParamsTrait>()

    // maps endpoint parameter name -> input member shape
    private val inputContextParams = buildMap<String, MemberShape> {
        opIndex.getInput(op).getOrNull()?.members()?.forEach { member ->
            member.getTrait<ContextParamTrait>()?.let { trait ->
                put(trait.name, member)
            }
        }
    }
    private val clientContextParams = service.getTrait<ClientContextParamsTrait>()

    fun render() {
        rules.parameters.toList().forEach(::renderBinding)
    }

    /**
     * Render the assignment of an endpoint param value within a builder. The SEP dictates a specific source order to
     * use when determining how to bind:
     * 1. staticContextParams (from operation shape)
     * 2. contextParam (from member of operation input shape)
     * 3. clientContextParams (from service shape)
     * 4. builtin binding
     * 5. builtin default
     *
     * Sources 4 and 5 are SDK-specific, builtin bindings are plugged in and rendered beforehand such that any bindings
     * from source 1 or 2 can supersede them.
     */
    private fun renderBinding(param: Parameter) {
        val paramName = param.name.asString()
        val paramDefaultName = param.defaultName()

        staticContextParams?.parameters?.get(paramName)?.let {
            writer.writeInline("#L = ", paramDefaultName)
            when (param.type) {
                ParameterType.STRING -> writer.write("#S", it.value.expectStringNode().value)
                ParameterType.BOOLEAN -> writer.write("#L", it.value.expectBooleanNode().value)
                else -> throw CodegenException("unexpected static context param type ${param.type}")
            }
            return
        }

        inputContextParams[paramName]?.let {
            writer.write("#L = #L#L", paramDefaultName, inputPrefix, it.defaultName())
            return
        }

        clientContextParams?.parameters?.get(paramName)?.let {
            writer.write("#1L = config.#1L", paramDefaultName)
        }
    }
}
