/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.auth

import aws.smithy.kotlin.codegen.KotlinSettings
import aws.smithy.kotlin.codegen.core.CodegenContext
import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.core.RuntimeTypes
import aws.smithy.kotlin.codegen.integration.AppendingSectionWriter
import aws.smithy.kotlin.codegen.integration.AuthSchemeHandler
import aws.smithy.kotlin.codegen.integration.KotlinIntegration
import aws.smithy.kotlin.codegen.integration.SectionWriterBinding
import aws.smithy.kotlin.codegen.lang.KotlinTypes
import aws.smithy.kotlin.codegen.model.asNullable
import aws.smithy.kotlin.codegen.model.buildSymbol
import aws.smithy.kotlin.codegen.model.hasTrait
import aws.smithy.kotlin.codegen.model.knowledge.AwsSignatureVersion4Asymmetric
import aws.smithy.kotlin.codegen.rendering.protocol.HttpProtocolClientGenerator
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import aws.smithy.kotlin.codegen.rendering.protocol.putIfAbsent
import aws.smithy.kotlin.codegen.rendering.util.ConfigProperty
import software.amazon.smithy.aws.traits.auth.SigV4ATrait
import software.amazon.smithy.aws.traits.auth.UnsignedPayloadTrait
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.ServiceIndex
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId

/**
 * Register support for the `aws.auth#sigv4a` auth scheme.
 */
class SigV4AsymmetricAuthSchemeIntegration : KotlinIntegration {
    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean = ServiceIndex
        .of(model)
        .getAuthSchemes(settings.service)
        .values
        .any { it.javaClass == SigV4ATrait::class.java }

    override fun authSchemes(ctx: ProtocolGenerator.GenerationContext): List<AuthSchemeHandler> = listOf(SigV4AsymmetricAuthSchemeHandler())

    override fun additionalServiceConfigProps(ctx: CodegenContext): List<ConfigProperty> = listOf(
        ConfigProperty {
            name = "sigV4aSigningRegionSet"
            symbol = KotlinTypes.Collections.set(KotlinTypes.String).asNullable()
            baseClass = RuntimeTypes.Auth.Credentials.AwsCredentials.SigV4aClientConfig
            useNestedBuilderBaseClass()
            documentation = """
                The set of regions to use when signing a request with SigV4a. If not provided this will automatically be set by the SDK.
            """.trimIndent()
        },
    )

    override val sectionWriters: List<SectionWriterBinding>
        get() = listOf(
            SectionWriterBinding(HttpProtocolClientGenerator.MergeServiceDefaults, renderMergeServiceDefaults),
        )
}

private val renderMergeServiceDefaults = AppendingSectionWriter { writer ->
    writer.putIfAbsent(
        RuntimeTypes.Auth.Signing.AwsSigningCommon.AwsSigningAttributes,
        "ConfigSigningRegionSet",
        "config.sigV4aSigningRegionSet",
        true,
    )
}

private class SigV4AsymmetricAuthSchemeHandler : AuthSchemeHandler {
    override val authSchemeId: ShapeId = SigV4ATrait.ID

    override val authSchemeIdSymbol: Symbol = buildSymbol {
        name = "AuthSchemeId.AwsSigV4Asymmetric"
        val ref = RuntimeTypes.Auth.Identity.AuthSchemeId
        objectRef = ref
        namespace = ref.namespace
        reference(ref, SymbolReference.ContextOption.USE)
    }

    override fun identityProviderAdapterExpression(writer: KotlinWriter) {
        writer.write("config.credentialsProvider")
    }

    override fun authSchemeProviderInstantiateAuthOptionExpr(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape?,
        writer: KotlinWriter,
    ) {
        val expr = if (op?.hasTrait<UnsignedPayloadTrait>() == true) {
            "#T(unsignedPayload = true)"
        } else {
            "#T()"
        }
        writer.write(expr, RuntimeTypes.Auth.HttpAuthAws.sigV4A)
    }

    override fun instantiateAuthSchemeExpr(ctx: ProtocolGenerator.GenerationContext, writer: KotlinWriter) {
        val signingService = AwsSignatureVersion4Asymmetric.signingServiceName(ctx.service)
        writer.write("#T(#T, #S)", RuntimeTypes.Auth.HttpAuthAws.SigV4AsymmetricAuthScheme, RuntimeTypes.Auth.Signing.AwsSigningStandard.DefaultAwsSigner, signingService)
    }
}
