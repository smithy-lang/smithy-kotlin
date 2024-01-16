/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.auth

import software.amazon.smithy.aws.traits.auth.SigV4ATrait
import software.amazon.smithy.aws.traits.auth.UnsignedPayloadTrait
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.integration.AuthSchemeHandler
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.model.knowledge.AwsSignatureVersion4Asymmetric
import software.amazon.smithy.kotlin.codegen.rendering.protocol.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.ServiceIndex
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId

/**
 * Register support for the `aws.auth#sigv4a` auth scheme.
 */
class SigV4AsymmetricAuthSchemeIntegration : KotlinIntegration {
    override val order: Byte = -59

    // Needs to be true due to the way integrations are filtered out before application and sigV4a customization.
    // See 'CodegenVisitor' & 'SigV4AsymmetricTraitCustomization'
    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean = true

    override fun authSchemes(ctx: ProtocolGenerator.GenerationContext): List<AuthSchemeHandler> =
        if (modelHasSigV4aTrait(ctx)) listOf(SigV4AsymmetricAuthSchemeHandler()) else super.authSchemes(ctx)
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

internal fun modelHasSigV4aTrait(ctx: ProtocolGenerator.GenerationContext): Boolean =
    ServiceIndex
        .of(ctx.model)
        .getAuthSchemes(ctx.service)
        .values
        .any { it.javaClass == SigV4ATrait::class.java }
