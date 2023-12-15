/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.endpoints

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
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
}
