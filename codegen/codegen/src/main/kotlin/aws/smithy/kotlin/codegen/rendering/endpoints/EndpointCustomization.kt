/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.endpoints

import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.rulesengine.language.EndpointRuleSet

/**
 * Extension point for integrations to customize endpoint resolution
 */
interface EndpointCustomization {
    /**
     * Endpoint ruleset function names to the symbol that implements the function.
     */
    val externalFunctions: Map<String, Symbol>
        get() = emptyMap()

    /**
     * Custom endpoint property renderer(s). Map of property names recognized to the renderer that should be used
     * to render that property value.
     */
    val propertyRenderers: Map<String, EndpointPropertyRenderer>
        get() = emptyMap()

    /**
     * Invoked by the [EndpointResolverAdapterGenerator] to allow customizations to handle binding builtins however
     * they choose.
     *
     * This function is invoked with the current writer context as:
     *
     * ```
     * internal fun resolveEndpointParameters(config: <ServiceClient>.Config, request: ResolveEndpointRequest): EndpointParameters {",
     *     <-- CURRENT WRITER CONTEXT -->
     * }
     * ```
     *
     * @param ctx The codegen generation context
     * @param rules The endpoint rules
     * @param writer The writer to render to
     */
    fun renderBindEndpointBuiltins(ctx: ProtocolGenerator.GenerationContext, rules: EndpointRuleSet, writer: KotlinWriter) {}

    /**
     * Invoked by the [EndpointParametersGenerator] for internal properties to add to the endpoint parameters, for state
     * that isn't an endpoint rule parameter. They're kept by `copy` and aren't part of `equals`, `hashCode`, or
     * `toString`.
     *
     * @param ctx The codegen generation context
     * @param rules The endpoint rules
     */
    fun internalEndpointParameters(ctx: ProtocolGenerator.GenerationContext, rules: EndpointRuleSet): List<InternalEndpointParameter> = emptyList()

    /**
     * Invoked by the [DefaultEndpointProviderGenerator] to render code that runs before the rules are evaluated.
     *
     * This function is invoked with the current writer context as:
     *
     * ```
     * public override suspend fun resolveEndpoint(params: EndpointParameters): Endpoint {
     *     <-- CURRENT WRITER CONTEXT -->
     *     ...
     * }
     * ```
     *
     * @param ctx The codegen generation context
     * @param rules The endpoint rules
     * @param writer The writer to render to
     */
    fun renderPreResolve(ctx: ProtocolGenerator.GenerationContext, rules: EndpointRuleSet, writer: KotlinWriter) {}
}

/**
 * An internal property of the generated endpoint parameters. See [EndpointCustomization.internalEndpointParameters].
 *
 * @param name The property name
 * @param symbol The property type
 * @param defaultValue The default value, as a Kotlin literal
 * @param documentation The property documentation
 */
class InternalEndpointParameter(
    val name: String,
    val symbol: Symbol,
    val defaultValue: String,
    val documentation: String,
)
