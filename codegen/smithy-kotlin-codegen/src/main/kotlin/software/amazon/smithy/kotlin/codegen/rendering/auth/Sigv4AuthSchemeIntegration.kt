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
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.model.knowledge.AwsSignatureVersion4
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
    override val order: Byte = -50

    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean =
        AwsSignatureVersion4.isSupportedAuthentication(model, settings.getService(model))

    override fun authSchemes(ctx: ProtocolGenerator.GenerationContext): List<AuthSchemeHandler> = listOf(SigV4AuthSchemeHandler())

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


open class SigV4AuthSchemeHandler : AuthSchemeHandler {
    override val authSchemeId: ShapeId = SigV4Trait.ID

    override fun identityProviderAdapterExpression(writer: KotlinWriter) {
        writer.write("config.credentialsProvider")
    }

    override fun authSchemeProviderInstantiateAuthOptionExpr(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape?,
        writer: KotlinWriter
    ) {
        val expr = if (op?.hasTrait<UnsignedPayloadTrait>() == true) {
            "sigv4(unsignedPayload = true)"
        }else {
            "sigv4()"
        }
        writer.write(expr)
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

    override fun instantiateAuthSchemeExpr(ctx: ProtocolGenerator.GenerationContext, writer: KotlinWriter) {
        // TODO - allow overriding signing config
        val signingService = AwsSignatureVersion4.signingServiceName(ctx.service)
        writer.write("#T(#T, #S)", RuntimeTypes.Auth.HttpAuthAws.SigV4AuthScheme, RuntimeTypes.Auth.Signing.AwsSigningStandard.DefaultAwsSigner, signingService)
        //     // AwsHttpSigner.Config().apply {
        //     //     signer = awsSigner
        //     //     service = serviceName
        //     // },
        //     return RenderExpr("#T()", RuntimeTypes.Auth.HttpAuthAws.SigV4AuthScheme)
    }
}