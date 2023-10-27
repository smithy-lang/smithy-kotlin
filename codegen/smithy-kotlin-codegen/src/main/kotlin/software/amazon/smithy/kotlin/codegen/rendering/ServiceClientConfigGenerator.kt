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
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.EndpointParametersGenerator
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
        add(RuntimeConfigProperty.ClientName)
        add(RuntimeConfigProperty.LogMode)
        if (context.protocolGenerator?.applicationProtocol?.isHttpProtocol == true) {
            add(RuntimeConfigProperty.HttpClient)
            add(RuntimeConfigProperty.HttpInterceptors)
            add(RuntimeConfigProperty.AuthSchemes)
        }
        if (shape.hasIdempotentTokenMember(context.model)) {
            add(RuntimeConfigProperty.IdempotencyTokenProvider)
        }

        add(RuntimeConfigProperty.RetryPolicy)
        add(RuntimeConfigProperty.RetryStrategy)
        add(RuntimeConfigProperty.TelemetryProvider)

        if (shape.hasTrait<ClientContextParamsTrait>()) {
            addAll(clientContextConfigProps(shape.expectTrait()))
        }

        // FIXME - we only generate an endpoint provider type if we have a protocol generator defined
        if (context.protocolGenerator != null) {
            add(
                ConfigProperty {
                    val hasRules = shape.hasTrait<EndpointRuleSetTrait>()
                    val defaultEndpointProviderSymbol = DefaultEndpointProviderGenerator.getSymbol(context.settings)
                    symbol = EndpointProviderGenerator.getSymbol(context.settings)
                    name = "endpointProvider"
                    propertyType = if (hasRules) { // if there's a ruleset, we have a usable default, otherwise caller has to provide their own
                        ConfigPropertyType.RequiredWithDefault("${defaultEndpointProviderSymbol.name}()")
                    } else {
                        ConfigPropertyType.Required()
                    }
                    documentation = """
                        The endpoint provider used to determine where to make service requests. **This is an advanced config
                        option.**

                        Endpoint resolution occurs as part of the workflow for every request made via the service client.

                        The inputs to endpoint resolution are defined on a per-service basis (see [EndpointParameters]).
                    """.trimIndent()
                    additionalImports = buildList {
                        add(EndpointParametersGenerator.getSymbol(context.settings))
                        if (hasRules) {
                            add(defaultEndpointProviderSymbol)
                        }
                    }
                },
            )
        }
    }

    /**
     * Derives client config properties from the service context params trait.
     */
    private fun clientContextConfigProps(trait: ClientContextParamsTrait): List<ConfigProperty> =
        trait
            .parameters
            .map { (k, v) ->
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
                }
            }

    fun render(ctx: CodegenContext, writer: KotlinWriter) = render(ctx, emptyList(), writer)

    override fun render(ctx: CodegenContext, props: Collection<ConfigProperty>, writer: KotlinWriter) {
        val allPropsByName = props.byName().toMutableMap()
        if (detectDefaultProps) {
            val defaultPropsByName = detectDefaultProps(ctx, serviceShape).byName()
            // register auto detected properties
            allPropsByName.putAll(defaultPropsByName)
        }

        // register properties from integrations
        ctx
            .integrations
            .map { it.additionalServiceConfigProps(ctx).byName() }
            .forEach(allPropsByName::putAll)

        super.render(ctx, allPropsByName.values.toList(), writer)
    }

    override fun renderBuilderBuildMethod(writer: KotlinWriter) {
        // we should _ALWAYS_ end up with SdkClientConfig.Builder as a base class for service client config, need
        // to override the build() method we inherit rather than use the default generated `internal` one
        writer.write("override fun build(): #configClass.name:L = #configClass.name:L(this)")
    }
}

private fun Collection<ConfigProperty>.byName(): Map<String, ConfigProperty> = associateBy(ConfigProperty::propertyName)
