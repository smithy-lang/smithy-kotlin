/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.endpoints

import software.amazon.smithy.aws.traits.ServiceTrait
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.model.*
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigProperty
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigPropertyType
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.rulesengine.language.EndpointRuleSet
import software.amazon.smithy.rulesengine.language.syntax.parameters.Parameter

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
                    BUILTIN_NAME ->
                        writer.write("#L = config.#L?.toString()", it.defaultName(), EndpointUrlProp.propertyName)
                }
            }
        }
    }
}
