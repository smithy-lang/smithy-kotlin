/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.endpoints

import aws.smithy.kotlin.codegen.KotlinSettings
import aws.smithy.kotlin.codegen.core.CodegenContext
import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.core.RuntimeTypes
import aws.smithy.kotlin.codegen.core.withBlock
import aws.smithy.kotlin.codegen.integration.KotlinIntegration
import aws.smithy.kotlin.codegen.lang.KotlinTypes
import aws.smithy.kotlin.codegen.model.asNullable
import aws.smithy.kotlin.codegen.model.defaultName
import aws.smithy.kotlin.codegen.model.expectShape
import aws.smithy.kotlin.codegen.model.getEndpointRules
import aws.smithy.kotlin.codegen.model.hasTrait
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import aws.smithy.kotlin.codegen.rendering.util.ConfigProperty
import aws.smithy.kotlin.codegen.rendering.util.ConfigPropertyType
import software.amazon.smithy.aws.traits.ServiceTrait
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.rulesengine.language.EndpointRuleSet
import software.amazon.smithy.rulesengine.language.syntax.expressions.Expression
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.LibraryFunction
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.ParseUrl
import software.amazon.smithy.rulesengine.language.syntax.parameters.Parameter
import software.amazon.smithy.rulesengine.language.syntax.rule.EndpointRule
import software.amazon.smithy.rulesengine.language.syntax.rule.Rule
import software.amazon.smithy.rulesengine.language.syntax.rule.TreeRule

/**
 * Registers support for the `SDK::Endpoint` builtin parameter
 */
class SdkEndpointBuiltinIntegration : KotlinIntegration {
    companion object {
        const val BUILTIN_NAME = "SDK::Endpoint"

        val EndpointUrlProp = ConfigProperty {
            name = "endpointUrl"
            symbol = RuntimeTypes.Core.Net.Url.Url.asNullable()
            documentation = """
                A custom endpoint to route requests to. The endpoint set here is passed to the configured
                [endpointProvider], which may inspect and modify it as needed.

                Setting a custom endpointUrl should generally be preferred to overriding the [endpointProvider] and is
                the recommended way to route requests to development or preview instances of a service.

                **This is an advanced config option.**
            """.trimIndent()
            propertyType = ConfigPropertyType.SymbolDefault
        }

        private val BoundByClient = InternalEndpointParameter(
            name = "boundByClient",
            symbol = KotlinTypes.Boolean,
            defaultValue = "false",
            documentation = "True if a service client bound these parameters from its config. The default endpoint " +
                "provider then evaluates the rules without the query of the endpoint, which `parseURL` rejects.",
        )
    }

    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean {
        val service = model.expectShape<ServiceShape>(settings.service)
        val isAwsSdk = service.hasTrait<ServiceTrait>()
        return isAwsSdk || service.getEndpointRules()?.parameters?.find { it.isBuiltIn && it.builtIn.get() == BUILTIN_NAME } != null
    }

    override fun additionalServiceConfigProps(ctx: CodegenContext): List<ConfigProperty> = listOf(EndpointUrlProp)

    override fun customizeEndpointResolution(ctx: ProtocolGenerator.GenerationContext): EndpointCustomization = object : EndpointCustomization {
        override fun renderBindEndpointBuiltins(
            ctx: ProtocolGenerator.GenerationContext,
            rules: EndpointRuleSet,
            writer: KotlinWriter,
        ) {
            val builtins = rules.parameters?.toList()?.filter(Parameter::isBuiltIn) ?: return
            builtins.forEach {
                when (it.builtIn.get()) {
                    BUILTIN_NAME -> {
                        writer.write("#L = config.#L?.toString()", it.defaultName(), EndpointUrlProp.propertyName)
                        if (rules.onlyParsesAsUrl(it)) writer.write("#L = true", BoundByClient.name)
                    }
                }
            }
        }

        override fun internalEndpointParameters(
            ctx: ProtocolGenerator.GenerationContext,
            rules: EndpointRuleSet,
        ): List<InternalEndpointParameter> = if (rules.queryRemovableEndpoint() != null) listOf(BoundByClient) else emptyList()

        override fun renderPreResolve(ctx: ProtocolGenerator.GenerationContext, rules: EndpointRuleSet, writer: KotlinWriter) {
            val endpoint = rules.queryRemovableEndpoint()?.defaultName() ?: return
            writer.withBlock("if (params.#L) {", "}", BoundByClient.name) {
                write("val endpointWithoutQuery = #T(params.#L)", RuntimeTypes.SmithyClient.Endpoints.withoutQuery, endpoint)
                write("if (endpointWithoutQuery != params.#1L) return resolveEndpoint(params.copy { #1L = endpointWithoutQuery })", endpoint)
            }
        }
    }
}

/**
 * The `SDK::Endpoint` parameter, if the rules only use it through `parseURL`
 */
private fun EndpointRuleSet.queryRemovableEndpoint(): Parameter? = parameters
    ?.find { it.isBuiltIn && it.builtIn.get() == SdkEndpointBuiltinIntegration.BUILTIN_NAME }
    ?.takeIf { onlyParsesAsUrl(it) }

/**
 * True if the rules pass [param] to `parseURL` (which rejects a query) and never use it directly as an endpoint URL, so
 * its query can't reach a resolved endpoint and is safe to remove.
 */
private fun EndpointRuleSet.onlyParsesAsUrl(param: Parameter): Boolean {
    val paramName = param.name.toString()
    val allRules = rules.flatMap(Rule::withNestedRules)

    val parsesUrl = allRules.any { rule -> rule.conditions.any { it.function.callsParseUrl(paramName) } }
    val usesAsUrl = allRules.any { it is EndpointRule && paramName in it.endpoint.url.references }

    return parsesUrl && !usesAsUrl
}

private fun Rule.withNestedRules(): List<Rule> = when (this) {
    is TreeRule -> listOf(this) + rules.flatMap(Rule::withNestedRules)
    else -> listOf(this)
}

private fun Expression.callsParseUrl(paramName: String): Boolean = this is LibraryFunction &&
    ((name == ParseUrl.ID && paramName in references) || arguments.any { it.callsParseUrl(paramName) })
