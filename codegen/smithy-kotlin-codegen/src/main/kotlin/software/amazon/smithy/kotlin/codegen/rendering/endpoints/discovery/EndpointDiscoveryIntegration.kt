/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.endpoints.discovery

import software.amazon.smithy.aws.traits.clientendpointdiscovery.ClientDiscoveredEndpointTrait
import software.amazon.smithy.aws.traits.clientendpointdiscovery.ClientEndpointDiscoveryTrait
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.integration.SectionWriterBinding
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.model.*
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.EndpointResolverAdapterGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.HttpProtocolClientGenerator
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigProperty
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape

class EndpointDiscoveryIntegration : KotlinIntegration {
    override fun additionalServiceConfigProps(ctx: CodegenContext): List<ConfigProperty> {
        val endpointDiscoveryOptional = ctx
            .model
            .operationShapes
            .any { it.getTrait<ClientDiscoveredEndpointTrait>()?.isRequired == false }

        val additionalProps =
            if (endpointDiscoveryOptional) {
                listOf(
                    ConfigProperty {
                        name = "useEndpointDiscovery"
                        documentation = "Whether to use automatic endpoint discovery for operations where it is optional."
                        useSymbolWithNullableBuilder(KotlinTypes.Boolean, "false")
                    },
                )
            } else {
                listOf()
            }

        return super.additionalServiceConfigProps(ctx) + additionalProps
    }

    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean =
        model.expectShape<ServiceShape>(settings.service).hasTrait<ClientEndpointDiscoveryTrait>()

    override val sectionWriters: List<SectionWriterBinding> = listOf(
        SectionWriterBinding(HttpProtocolClientGenerator.EndpointResolverAdapterBinding, ::renderEndpointResolver),
        SectionWriterBinding(HttpProtocolClientGenerator.AdditionalMethodsSection, ::renderDiscoverEndpoint),
    )

    private fun renderDiscoverEndpoint(writer: KotlinWriter, previousValue: String?) {
        previousValue?.let(writer::write)

        val ctx = writer.getContextValue(HttpProtocolClientGenerator.AdditionalMethodsSection.GenerationContext)
        val model = ctx.model
        val discoveryTrait = ctx.service.expectTrait<ClientEndpointDiscoveryTrait>()
        val op = model.expectShape<OperationShape>(discoveryTrait.operation)

        writer.withBlock(
            "private val discoveredEndpointResolver = #T(#T(config), config::region) {",
            "}",
            RuntimeTypes.EndpointDiscovery.DiscoveredEndpointResolver,
            EndpointResolverAdapterGenerator.getSymbol(ctx.settings),
        ) {
            // ASSUMPTION No services which use discovery include parameters to the EP operation (despite being
            // possible according to the Smithy spec).
            write("#L()", op.defaultName())
            indent()
            write(".endpoints")
            withBlock("?.map { ep -> #T(", ")}", RuntimeTypes.Core.Utils.ExpiringValue) {
                write("#T.parse(ep.address!!),", RuntimeTypes.Core.Net.Host)
                write("#T.now() + ep.cachePeriodInMinutes.#T", RuntimeTypes.Core.Instant, KotlinTypes.Time.minutes)
            }
            write("?: #T()", KotlinTypes.Collections.listOf)
            dedent()
        }

        writer.write("")
    }

    private fun renderEndpointResolver(writer: KotlinWriter, previousValue: String?) {
        val ctx = writer.getContextValue(HttpProtocolClientGenerator.EndpointResolverAdapterBinding.GenerationContext)
        val op = writer.getContextValue(HttpProtocolClientGenerator.EndpointResolverAdapterBinding.OperationShape)

        when (op.getTrait<ClientDiscoveredEndpointTrait>()?.isRequired) {
            null -> writer.write("#L", previousValue)
            true -> writer.write("execution.endpointResolver = discoveredEndpointResolver")
            false -> writer.write(
                "execution.endpointResolver = if (config.useEndpointDiscovery) discoveredEndpointResolver else #T(config)",
                EndpointResolverAdapterGenerator.getSymbol(ctx.settings),
            )
        }
    }
}
