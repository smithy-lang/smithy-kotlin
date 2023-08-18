/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.auth

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.integration.AuthSchemeHandler
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId

// FIXME - replace with NoAuthTrait when we upgrade to a version where it exists
public val AnonymousAuthSchemeId: ShapeId = ShapeId.from("smithy.api#noAuth")

/**
 * Register support for the `smithy.api#optionalAuth` auth scheme.
 */
class AnonymousAuthSchemeIntegration : KotlinIntegration {
    override fun authSchemes(ctx: ProtocolGenerator.GenerationContext): List<AuthSchemeHandler> = listOf(AnonymousAuthSchemeHandler())
}

class AnonymousAuthSchemeHandler : AuthSchemeHandler {
    override val authSchemeId: ShapeId = AnonymousAuthSchemeId
    override val authSchemeIdSymbol: Symbol = buildSymbol {
        name = "AuthSchemeId.Anonymous"
        val ref = RuntimeTypes.Auth.Identity.AuthSchemeId
        objectRef = ref
        namespace = ref.namespace
        reference(ref, SymbolReference.ContextOption.USE)
    }

    override fun identityProviderAdapterExpression(writer: KotlinWriter) {
        writer.write("#T", RuntimeTypes.Auth.HttpAuth.AnonymousIdentityProvider)
    }

    override fun authSchemeProviderInstantiateAuthOptionExpr(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape?,
        writer: KotlinWriter,
    ) {
        writer.write("#T(#T.Anonymous)", RuntimeTypes.Auth.Identity.AuthOption, RuntimeTypes.Auth.Identity.AuthSchemeId)
    }

    override fun instantiateAuthSchemeExpr(ctx: ProtocolGenerator.GenerationContext, writer: KotlinWriter) {
        writer.write("#T", RuntimeTypes.Auth.HttpAuth.AnonymousAuthScheme)
    }
}
