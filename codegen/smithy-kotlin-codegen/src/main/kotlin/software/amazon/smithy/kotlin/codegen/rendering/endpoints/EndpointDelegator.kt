/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.endpoints

import software.amazon.smithy.kotlin.codegen.core.useFileWriter
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.rulesengine.language.EndpointRuleSet
import software.amazon.smithy.rulesengine.traits.EndpointTestCase

/**
 * Responsible for providing various endpoint related generation components
 */
interface EndpointDelegator {
    companion object {
        /**
         * Get the default endpoint delegator
         */
        val Default: EndpointDelegator = object : EndpointDelegator {}
    }

    /**
     * Generate a default implementation for the modeled endpoint provider.
     * Will only be invoked when a model's service shape has the necessary endpoint rule set trait, however, the base
     * interface and parameter type will always be generated (the expectation being that the caller supplies their own
     * at runtime).
     */
    fun generateEndpointProvider(ctx: ProtocolGenerator.GenerationContext, rules: EndpointRuleSet?) {
        val paramsSymbol = EndpointParametersGenerator.getSymbol(ctx.settings)
        val providerSymbol = EndpointProviderGenerator.getSymbol(ctx.settings)
        val defaultProviderSymbol = DefaultEndpointProviderGenerator.getSymbol(ctx.settings)

        ctx.delegator.useFileWriter(providerSymbol) {
            EndpointProviderGenerator(it, ctx.settings, providerSymbol, paramsSymbol).render()
        }

        if (rules != null) {
            ctx.delegator.useFileWriter(defaultProviderSymbol) {
                DefaultEndpointProviderGenerator(it, rules, defaultProviderSymbol, providerSymbol, paramsSymbol, ctx.settings).render()
            }
        }
    }

    /**
     * Generate the input parameter type for the generated endpoint provider implementation.
     */
    fun generateEndpointParameters(ctx: ProtocolGenerator.GenerationContext, rules: EndpointRuleSet?) {
        val paramsSymbol = EndpointParametersGenerator.getSymbol(ctx.settings)
        ctx.delegator.useFileWriter(paramsSymbol) {
            EndpointParametersGenerator(it, ctx.settings, rules, paramsSymbol).render()
        }
    }

    /**
     * Generate unit tests for the modeled endpoint rules test cases.
     * Will only be invoked when a model's service shape has both the rule set and test case traits for endpoints.
     */
    fun generateEndpointProviderTests(ctx: ProtocolGenerator.GenerationContext, tests: List<EndpointTestCase>, rules: EndpointRuleSet) {
        val paramsSymbol = EndpointParametersGenerator.getSymbol(ctx.settings)
        val defaultProviderSymbol = DefaultEndpointProviderGenerator.getSymbol(ctx.settings)
        val testSymbol = DefaultEndpointProviderTestGenerator.getSymbol(ctx.settings)

        ctx.delegator.useTestFileWriter("${testSymbol.name}.kt", testSymbol.namespace) {
            DefaultEndpointProviderTestGenerator(it, rules, tests, defaultProviderSymbol, paramsSymbol).render()
        }
    }

    /**
     * Generate the operation adapter for going from the type agnostic HTTP endpoint resolver to the generated
     * endpoint provider.
     */
    fun generateEndpointResolverAdapter(ctx: ProtocolGenerator.GenerationContext) {
        ctx.delegator.useFileWriter(EndpointResolverAdapterGenerator.getSymbol(ctx.settings)) {
            EndpointResolverAdapterGenerator(ctx, it).render()
        }
    }
}
