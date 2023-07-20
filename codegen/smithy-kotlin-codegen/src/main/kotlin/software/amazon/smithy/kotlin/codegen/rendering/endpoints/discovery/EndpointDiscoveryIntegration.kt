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
import software.amazon.smithy.kotlin.codegen.model.*
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.EndpointResolverAdapterGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.HttpProtocolClientGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolMiddleware
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigProperty
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape

class EndpointDiscoveryIntegration : KotlinIntegration {
    override fun additionalServiceConfigProps(ctx: CodegenContext): List<ConfigProperty> {
        val endpointDiscoveryOptional = ctx
            .model
            .operationShapes
            .none { it.getTrait<ClientDiscoveredEndpointTrait>()?.isRequired == true }
        val discovererSymbol = EndpointDiscovererGenerator.symbolFor(ctx.settings)
        return super.additionalServiceConfigProps(ctx) + listOf(
            ConfigProperty {
                name = "endpointDiscoverer"

                if (endpointDiscoveryOptional) {
                    documentation = """
                        The endpoint discoverer for this client, if applicable. By default, no endpoint
                        discovery is provided. To use endpoint discovery, set this to a valid
                        [${discovererSymbol.name}] instance.
                    """.trimIndent()
                    symbol = discovererSymbol.asNullable()
                } else {
                    documentation = "The endpoint discoverer for this client"
                    useSymbolWithNullableBuilder(discovererSymbol, "${discovererSymbol.name}()")
                }
            },
        )
    }

    override fun customizeMiddleware(
        ctx: ProtocolGenerator.GenerationContext,
        resolved: List<ProtocolMiddleware>,
    ): List<ProtocolMiddleware> = super.customizeMiddleware(ctx, resolved) + listOf(DiscoveredEndpointMiddleware)

    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean =
        model.expectShape<ServiceShape>(settings.service).hasTrait<ClientEndpointDiscoveryTrait>()

    override val sectionWriters: List<SectionWriterBinding> = listOf(
        SectionWriterBinding(HttpProtocolClientGenerator.EndpointResolverAdapterBinding, ::renderEndpointResolver),
    )

    private fun renderEndpointResolver(writer: KotlinWriter, previousValue: String?) {
        val ctx = writer.getContextValue(HttpProtocolClientGenerator.EndpointResolverAdapterBinding.GenerationContext)
        val op = writer.getContextValue(HttpProtocolClientGenerator.EndpointResolverAdapterBinding.OperationShape)
        val clientSymbol = ctx.symbolProvider.toSymbol(ctx.service)
        val defaultClientName = "Default${clientSymbol.name}"

        when (op.getTrait<ClientDiscoveredEndpointTrait>()?.isRequired) {
            null -> writer.write("#L", previousValue)

            true -> writer.write(
                "execution.endpointResolver = config.endpointDiscoverer.asEndpointResolver(this@#L, #T(config))",
                defaultClientName,
                EndpointResolverAdapterGenerator.getSymbol(ctx.settings),
            )

            false -> writer.write(
                "execution.endpointResolver = config.endpointDiscoverer?.asEndpointResolver(this@#1L, #2T(config)) ?: #2T(config)",
                defaultClientName,
                EndpointResolverAdapterGenerator.getSymbol(ctx.settings),
            )
        }
    }

    override fun writeAdditionalFiles(ctx: CodegenContext, delegator: KotlinDelegator) {
        EndpointDiscovererGenerator(ctx, delegator).render()
        super.writeAdditionalFiles(ctx, delegator)
    }
}

private object DiscoveredEndpointMiddleware : ProtocolMiddleware {
    override val name: String = "DiscoveredEndpointMiddleware"

    override fun isEnabledFor(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Boolean =
        op.getTrait<ClientEndpointDiscoveryTrait>()?.optionalError?.getOrNull() != null &&
            op.hasTrait<ClientDiscoveredEndpointTrait>()

    override fun render(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
        val interceptor = buildSymbol {
            name = "DiscoveredEndpointErrorInterceptor"
            namespace(KotlinDependency.HTTP_CLIENT, "aws.smithy.kotlin.runtime.http.interceptors")
        }

        val errorShapeId = ctx.service.expectTrait<ClientEndpointDiscoveryTrait>().optionalError.get()
        val errorShape = ctx.model.expectShape(errorShapeId)
        val errorSymbol = ctx.symbolProvider.toSymbol(errorShape)
        writer.write(
            "config.endpointDiscoverer?.let { op.interceptors.add(#T(#T, it::invalidate)) }",
            interceptor,
            errorSymbol,
        )
    }
}
