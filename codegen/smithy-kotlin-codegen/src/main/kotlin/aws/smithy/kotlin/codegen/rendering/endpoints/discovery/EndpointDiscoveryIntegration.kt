/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.endpoints.discovery

import aws.smithy.kotlin.codegen.KotlinSettings
import aws.smithy.kotlin.codegen.core.CodegenContext
import aws.smithy.kotlin.codegen.core.KotlinDelegator
import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.core.RuntimeTypes
import aws.smithy.kotlin.codegen.core.getContextValue
import aws.smithy.kotlin.codegen.integration.KotlinIntegration
import aws.smithy.kotlin.codegen.integration.SectionWriterBinding
import aws.smithy.kotlin.codegen.model.asNullable
import aws.smithy.kotlin.codegen.model.expectShape
import aws.smithy.kotlin.codegen.model.expectTrait
import aws.smithy.kotlin.codegen.model.getTrait
import aws.smithy.kotlin.codegen.model.hasTrait
import aws.smithy.kotlin.codegen.rendering.endpoints.EndpointResolverAdapterGenerator
import aws.smithy.kotlin.codegen.rendering.protocol.HttpProtocolClientGenerator
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolMiddleware
import aws.smithy.kotlin.codegen.rendering.util.ConfigProperty
import aws.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.aws.traits.clientendpointdiscovery.ClientDiscoveredEndpointTrait
import software.amazon.smithy.aws.traits.clientendpointdiscovery.ClientEndpointDiscoveryTrait
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape

class EndpointDiscoveryIntegration : KotlinIntegration {
    companion object {
        const val CLIENT_CONFIG_NAME = "endpointDiscoverer"
        const val ORDER: Byte = 0 // doesn't depend on any other integrations

        fun isEnabledFor(model: Model, settings: KotlinSettings) = model
            .expectShape<ServiceShape>(settings.service)
            .hasTrait<ClientEndpointDiscoveryTrait>()

        fun isOptionalFor(ctx: CodegenContext) = ctx
            .model
            .operationShapes
            .none { it.getTrait<ClientDiscoveredEndpointTrait>()?.isRequired == true }
    }

    override fun additionalServiceConfigProps(ctx: CodegenContext): List<ConfigProperty> {
        val endpointDiscoveryOptional = isOptionalFor(ctx)
        val interfaceSymbol = EndpointDiscovererInterfaceGenerator.symbolFor(ctx.settings)
        return super.additionalServiceConfigProps(ctx) + listOf(
            ConfigProperty.Companion {
                name = CLIENT_CONFIG_NAME

                if (endpointDiscoveryOptional) {
                    documentation = """
                        The endpoint discoverer for this client, if applicable. By default, no endpoint discovery is
                        provided. To use endpoint discovery, set this to a valid [${interfaceSymbol.name}] instance.
                    """.trimIndent()
                    symbol = interfaceSymbol.asNullable()
                } else {
                    val defaultImplSymbol = DefaultEndpointDiscovererGenerator.symbolFor(ctx.settings)
                    documentation = "The endpoint discoverer for this client"
                    additionalImports = listOf(defaultImplSymbol)
                    useSymbolWithNullableBuilder(interfaceSymbol, "${defaultImplSymbol.name}()")
                }
            },
        )
    }

    override fun customizeMiddleware(
        ctx: ProtocolGenerator.GenerationContext,
        resolved: List<ProtocolMiddleware>,
    ): List<ProtocolMiddleware> = resolved + DiscoveredEndpointErrorMiddleware

    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean = isEnabledFor(model, settings)

    override val order = ORDER

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
                "execution.endpointResolver = config.#L.asEndpointResolver(this@#L, #T(config))",
                CLIENT_CONFIG_NAME,
                defaultClientName,
                EndpointResolverAdapterGenerator.Companion.getSymbol(ctx.settings),
            )

            false -> writer.write(
                "execution.endpointResolver = config.#1L?.asEndpointResolver(this@#2L, #3T(config)) ?: #3T(config)",
                CLIENT_CONFIG_NAME,
                defaultClientName,
                EndpointResolverAdapterGenerator.Companion.getSymbol(ctx.settings),
            )
        }
    }

    override fun writeAdditionalFiles(ctx: CodegenContext, delegator: KotlinDelegator) {
        EndpointDiscovererInterfaceGenerator(ctx, delegator).render()

        if (!isOptionalFor(ctx)) {
            DefaultEndpointDiscovererGenerator(ctx, delegator).render()
        }
    }
}

private object DiscoveredEndpointErrorMiddleware : ProtocolMiddleware {
    override val name: String = "DiscoveredEndpointErrorMiddleware"

    override fun isEnabledFor(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Boolean =
        ctx.service.getTrait<ClientEndpointDiscoveryTrait>()?.optionalError?.getOrNull() != null &&
            op.hasTrait<ClientDiscoveredEndpointTrait>()

    override fun render(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
        val errorShapeId = ctx.service.expectTrait<ClientEndpointDiscoveryTrait>().optionalError.get()
        val errorShape = ctx.model.expectShape(errorShapeId)
        val errorSymbol = ctx.symbolProvider.toSymbol(errorShape)
        writer.write(
            "config.#L?.let { op.interceptors.add(#T(#T::class, it::invalidate)) }",
            EndpointDiscoveryIntegration.CLIENT_CONFIG_NAME,
            RuntimeTypes.HttpClient.Interceptors.DiscoveredEndpointErrorInterceptor,
            errorSymbol,
        )
    }
}
