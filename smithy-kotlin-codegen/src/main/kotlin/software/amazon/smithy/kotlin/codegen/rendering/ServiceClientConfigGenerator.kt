/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.model.expectTrait
import software.amazon.smithy.kotlin.codegen.model.hasIdempotentTokenMember
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.DefaultEndpointProviderGenerator
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.EndpointProviderGenerator
import software.amazon.smithy.kotlin.codegen.rendering.util.AbstractConfigGenerator
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigProperty
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigPropertyType
import software.amazon.smithy.kotlin.codegen.rendering.util.RuntimeConfigProperty
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
 * @param serviceShape the service shape to render config for
 * @param detectDefaultProps Flag indicating if properties should be added automatically based on model detection
 */
class ServiceClientConfigGenerator(
    private val serviceShape: ServiceShape,
    private val detectDefaultProps: Boolean = true,
) : AbstractConfigGenerator() {

    /**
     * Attempt to detect configuration properties automatically based on the model
     */
    private fun detectDefaultProps(context: CodegenContext, shape: ServiceShape): List<ConfigProperty> = buildList {
        add(RuntimeConfigProperty.SdkLogMode)
        if (context.protocolGenerator?.applicationProtocol?.isHttpProtocol == true) {
            add(RuntimeConfigProperty.HttpClientEngine)
            add(RuntimeConfigProperty.HttpInterceptors)
        }
        if (shape.hasIdempotentTokenMember(context.model)) {
            add(RuntimeConfigProperty.IdempotencyTokenProvider)
        }

        add(RuntimeConfigProperty.RetryStrategy)
        add(RuntimeConfigProperty.Tracer)

        if (shape.hasTrait<ClientContextParamsTrait>()) {
            addAll(clientContextConfigProps(shape.expectTrait()))
        }

        add(
            ConfigProperty {
                val hasRules = shape.hasTrait<EndpointRuleSetTrait>()
                symbol = EndpointProviderGenerator.getSymbol(context.settings)
                propertyType = if (hasRules) { // if there's a ruleset, we have a usable default, otherwise caller has to provide their own
                    additionalImports = listOf(DefaultEndpointProviderGenerator.getSymbol(context.settings))
                    ConfigPropertyType.RequiredWithDefault("DefaultEndpointProvider()")
                } else {
                    ConfigPropertyType.Required()
                }
                documentation = """
                        The endpoint provider used to determine where to make service requests.
                """.trimIndent()
            },
        )
    }

    /**
     * Derives client config properties from the service context params trait.
     */
    private fun clientContextConfigProps(trait: ClientContextParamsTrait): List<ConfigProperty> = buildList {
        trait.parameters.forEach { (k, v) ->
            add(
                when (v.type) {
                    ShapeType.BOOLEAN -> ConfigProperty.Boolean(
                        name = k.toCamelCase(),
                        defaultValue = false,
                        documentation = v.documentation.getOrNull(),
                    )
                    ShapeType.STRING -> ConfigProperty.String(
                        name = k.toCamelCase(),
                        defaultValue = null,
                        documentation = v.documentation.getOrNull(),
                    )
                    else -> throw CodegenException("unsupported client context param type ${v.type}")
                },
            )
        }
    }

    fun render(ctx: CodegenContext, writer: KotlinWriter) = render(ctx, emptyList(), writer)

    override fun render(ctx: CodegenContext, props: Collection<ConfigProperty>, writer: KotlinWriter) {
        val allProps = props.toMutableList()
        if (detectDefaultProps) {
            // register auto detected properties
            allProps.addAll(detectDefaultProps(ctx, serviceShape))
        }

        // register properties from integrations
        val integrationProps = ctx.integrations.flatMap { it.additionalServiceConfigProps(ctx) }
        allProps.addAll(integrationProps)
        super.render(ctx, allProps, writer)
    }
}
