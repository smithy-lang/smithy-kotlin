/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.codegen.rendering.auth

import aws.smithy.kotlin.codegen.KotlinSettings
import aws.smithy.kotlin.codegen.core.CodegenContext
import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.core.RuntimeTypes
import aws.smithy.kotlin.codegen.core.getContextValue
import aws.smithy.kotlin.codegen.integration.AppendingSectionWriter
import aws.smithy.kotlin.codegen.integration.AuthSchemeHandler
import aws.smithy.kotlin.codegen.integration.KotlinIntegration
import aws.smithy.kotlin.codegen.integration.SectionWriterBinding
import aws.smithy.kotlin.codegen.model.buildSymbol
import aws.smithy.kotlin.codegen.rendering.protocol.HttpProtocolClientGenerator
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import aws.smithy.kotlin.codegen.rendering.util.ConfigProperty
import aws.smithy.kotlin.codegen.rendering.util.ConfigPropertyType
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.ServiceIndex
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.HttpBearerAuthTrait

/**
 * Register support for the `smithy.api#HTTPBearerAuth` auth scheme.
 */
class BearerTokenAuthSchemeIntegration : KotlinIntegration {
    // Allow integrations to customize the service config props, later integrations take precedence
    override val order: Byte = -50

    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean =
        ServiceIndex.of(model)
            .getAuthSchemes(settings.service)
            .containsKey(HttpBearerAuthTrait.ID)
    override fun authSchemes(ctx: ProtocolGenerator.GenerationContext): List<AuthSchemeHandler> = listOf(BearerTokenAuthSchemeHandler())

    override fun additionalServiceConfigProps(ctx: CodegenContext): List<ConfigProperty> {
        val bearerTokenProviderProp = ConfigProperty.Companion {
            name = "bearerTokenProvider"
            symbol = RuntimeTypes.Auth.HttpAuth.BearerTokenProvider
            baseClass = RuntimeTypes.Auth.HttpAuth.BearerTokenProviderConfig
            useNestedBuilderBaseClass()
            documentation = """
                The token provider to use for authenticating requests when using [${RuntimeTypes.Auth.HttpAuth.BearerTokenAuthScheme.fullName}].
                NOTE: The caller is responsible for managing the lifetime of the provider when set. The SDK
                client will not close it when the client is closed.
            """.trimIndent()

            // FIXME - this isn't necessarily required if a service supports multiple authentication traits...
            propertyType = ConfigPropertyType.Required()
        }

        return listOf(bearerTokenProviderProp)
    }

    override val sectionWriters: List<SectionWriterBinding>
        get() = listOf(
            SectionWriterBinding(HttpProtocolClientGenerator.ClientInitializer, renderClientInitializer),
        )

    private val renderClientInitializer = AppendingSectionWriter { writer ->
        val ctx = writer.getContextValue(HttpProtocolClientGenerator.ClientInitializer.GenerationContext)
        val serviceIndex = ServiceIndex.of(ctx.model)
        val hasBearerTokenAuth = serviceIndex
            .getAuthSchemes(ctx.settings.service)
            .containsKey(HttpBearerAuthTrait.ID)
        if (hasBearerTokenAuth) {
            writer.write("managedResources.#T(config.bearerTokenProvider)", RuntimeTypes.Core.IO.addIfManaged)
        }
    }
}

class BearerTokenAuthSchemeHandler : AuthSchemeHandler {
    override val authSchemeId: ShapeId = HttpBearerAuthTrait.ID

    override val authSchemeIdSymbol: Symbol = buildSymbol {
        name = "AuthSchemeId.HttpBearer"
        val ref = RuntimeTypes.Auth.Identity.AuthSchemeId
        objectRef = ref
        namespace = ref.namespace
        reference(ref, SymbolReference.ContextOption.USE)
    }

    override fun identityProviderAdapterExpression(writer: KotlinWriter) {
        writer.write("config.bearerTokenProvider")
    }

    override fun authSchemeProviderInstantiateAuthOptionExpr(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape?,
        writer: KotlinWriter,
    ) {
        writer.write("#T(#T.HttpBearer)", RuntimeTypes.Auth.Identity.AuthOption, RuntimeTypes.Auth.Identity.AuthSchemeId)
    }

    override fun instantiateAuthSchemeExpr(ctx: ProtocolGenerator.GenerationContext, writer: KotlinWriter) {
        writer.write("#T()", RuntimeTypes.Auth.HttpAuth.BearerTokenAuthScheme)
    }
}
