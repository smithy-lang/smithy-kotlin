/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.auth

import software.amazon.smithy.aws.traits.auth.SigV4Trait
import software.amazon.smithy.aws.traits.auth.UnsignedPayloadTrait
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.integration.AuthSchemeHandler
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.core.RenderExpr
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigProperty
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigPropertyType
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId


/**
 * Register support for the `aws.auth#sigv4` auth scheme.
 */
class Sigv4AuthSchemeIntegration : KotlinIntegration {
    // Allow integrations to customize the service config props, later integrations take precedence
    override val order: Byte = -100

    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean =
        AwsSignatureVersion4.isSupportedAuthentication(model, settings.getService(model))

    override fun authSchemes(ctx: ProtocolGenerator.GenerationContext): List<AuthSchemeHandler> = listOf(SigV4AuthSchemeHandler())

    // FIXME - register a base class for this
    override fun additionalServiceConfigProps(ctx: CodegenContext): List<ConfigProperty> {
        val credentialsProviderProp = ConfigProperty {
            symbol = RuntimeTypes.Auth.Credentials.AwsCredentials.CredentialsProvider
            documentation = """
                The AWS credentials provider to use for authenticating requests. 
                NOTE: The caller is responsible for managing the lifetime of the provider when set. The SDK
                client will not close it when the client is closed.
            """.trimIndent()

            propertyType = ConfigPropertyType.Required("")
        }

        return listOf(credentialsProviderProp)
    }
}


class SigV4AuthSchemeHandler : AuthSchemeHandler {
    override val authSchemeId: ShapeId = SigV4Trait.ID

    override fun identityProviderAdapterExpression(): RenderExpr = RenderExpr("config.credentialsProvider")

    override fun authSchemeProviderInstantiateAuthOptionExpr(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape?
    ): RenderExpr {
        val expr = if (op?.hasTrait<UnsignedPayloadTrait>() == true) {
            "sigv4(unsignedPayload = true)"
        }else {
            "sigv4()"
        }
        return RenderExpr(expr)
    }

    // FIXME: Move to runtime?
    override fun authSchemeProviderRenderAdditionalMethods(ctx: ProtocolGenerator.GenerationContext, writer: KotlinWriter) {
        super.authSchemeProviderRenderAdditionalMethods(ctx, writer)
        writer.withBlock(
            "private fun sigv4(unsignedPayload: Boolean = false): #T {",
            "}",
            RuntimeTypes.Auth.Identity.AuthSchemeOption
        ) {
            writer.write("val opt = #T(#T.AwsSigV4)", RuntimeTypes.Auth.Identity.AuthSchemeOption, RuntimeTypes.Auth.Identity.AuthSchemeId)
            writer.write(
                "if (unsignedPayload) opt.attributes[#T.HashSpecification] = #T.UnsignedPayload",
                RuntimeTypes.Auth.Signing.AwsSigningCommon.AwsSigningAttributes,
                RuntimeTypes.Auth.Signing.AwsSigningCommon.HashSpecification
            )
            writer.write("return opt")
        }
    }
}