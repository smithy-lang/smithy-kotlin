/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.auth

import software.amazon.smithy.aws.traits.auth.SigV4ATrait
import software.amazon.smithy.aws.traits.auth.SigV4Trait
import software.amazon.smithy.aws.traits.auth.UnsignedPayloadTrait
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.integration.AppendingSectionWriter
import software.amazon.smithy.kotlin.codegen.integration.AuthSchemeHandler
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.integration.SectionWriterBinding
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.model.knowledge.AwsSignatureVersion4Asymmetric
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.EndpointCustomization
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.EndpointPropertyRenderer
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.ExpressionRenderer
import software.amazon.smithy.kotlin.codegen.rendering.protocol.*
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigProperty
import software.amazon.smithy.kotlin.codegen.utils.dq
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.EventStreamIndex
import software.amazon.smithy.model.knowledge.ServiceIndex
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.rulesengine.language.syntax.expressions.Expression
import software.amazon.smithy.utils.SmithyInternalApi

class SigV4AsymmetricAuthSchemeIntegration : KotlinIntegration {
    // Needs to go after 'SigV4AsymmetricTraitCustomization'
    override val order: Byte = -59

    // Needs to be true due to the way integrations are filtered out before application. See 'CodegenVisitor'
    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean = true

    override fun authSchemes(ctx: ProtocolGenerator.GenerationContext): List<AuthSchemeHandler> =
        if (modelHasSigV4aTrait(ctx)) listOf(SigV4AsymmetricAuthSchemeHandler()) else super.authSchemes(ctx)

    override fun customizeMiddleware(ctx: ProtocolGenerator.GenerationContext, resolved: List<ProtocolMiddleware>): List<ProtocolMiddleware> =
        if (modelHasSigV4aTrait(ctx)) resolved + SigV4AsymmetricSignedBodyHeaderMiddleware() else super.customizeMiddleware(ctx, resolved)

    override fun additionalServiceConfigProps(ctx: CodegenContext): List<ConfigProperty> =
        if (modelHasSigV4aTrait(ctx)) listOf(credentialsProviderProp) else super.additionalServiceConfigProps(ctx)

    override fun customizeEndpointResolution(ctx: ProtocolGenerator.GenerationContext): EndpointCustomization? =
        if (modelHasSigV4aTrait(ctx)) SigV4AsymmetricEndpointCustomization else null

    override val sectionWriters: List<SectionWriterBinding> = listOf(
            SectionWriterBinding(HttpProtocolUnitTestRequestGenerator.ConfigureServiceClient, renderHttpProtocolRequestTestConfigureServiceClient),
            SectionWriterBinding(HttpProtocolUnitTestResponseGenerator.ConfigureServiceClient, renderHttpProtocolResponseTestConfigureServiceClient),
            SectionWriterBinding(HttpProtocolClientGenerator.ClientInitializer, renderClientInitializer),
            SectionWriterBinding(HttpProtocolClientGenerator.MergeServiceDefaults, renderMergeServiceDefaults),
        )
}

// set service client defaults for HTTP request protocol tests
private val renderHttpProtocolRequestTestConfigureServiceClient = AppendingSectionWriter { writer ->
    val ctx = writer.getContextValue(HttpProtocolUnitTestRequestGenerator.ConfigureServiceClient.Context)
    if (modelHasSigV4aTrait(ctx)) {
        val op = writer.getContextValue(HttpProtocolUnitTestRequestGenerator.ConfigureServiceClient.Operation)
        renderConfigureServiceClientForTest(ctx, op, writer)
    }
}

// set service client defaults for HTTP response protocol tests
private val renderHttpProtocolResponseTestConfigureServiceClient = AppendingSectionWriter { writer ->
    val ctx = writer.getContextValue(HttpProtocolUnitTestResponseGenerator.ConfigureServiceClient.Context)
    if (modelHasSigV4aTrait(ctx)) {
        val op = writer.getContextValue(HttpProtocolUnitTestResponseGenerator.ConfigureServiceClient.Operation)
        renderConfigureServiceClientForTest(ctx, op, writer)
    }
}

// add credentials to managed resources in the service client initializer
private val renderClientInitializer = AppendingSectionWriter { writer ->
    val ctx = writer.getContextValue(HttpProtocolClientGenerator.ClientInitializer.GenerationContext)
    if (modelHasSigV4aTrait(ctx)) {
        if (AwsSignatureVersion4Asymmetric.isSupportedAuthentication(ctx.model, ctx.settings.getService(ctx.model))) {
            writer.write("managedResources.#T(config.credentialsProvider)", RuntimeTypes.Core.IO.addIfManaged)
        }
    }
}

// render sigV4A related execution context properties
private val renderMergeServiceDefaults = AppendingSectionWriter { writer ->
    val ctx = writer.getContextValue(HttpProtocolClientGenerator.ClientInitializer.GenerationContext)

    if (modelHasSigV4aTrait(ctx)) {
        // default signing context (most of this has been moved to auth schemes but some things like event streams still depend on this)
        val signingServiceName = AwsSignatureVersion4Asymmetric.signingServiceName(ctx.model, ctx.service)
        writer.putIfAbsent(
            RuntimeTypes.Auth.Signing.AwsSigningCommon.AwsSigningAttributes,
            "SigningService",
            signingServiceName.dq(),
        )
        writer.putIfAbsent(RuntimeTypes.Auth.Signing.AwsSigningCommon.AwsSigningAttributes, "CredentialsProvider")
    }
}

private fun renderConfigureServiceClientForTest(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
    if (AwsSignatureVersion4Asymmetric.hasSigV4AAuthScheme(ctx.model, ctx.service, op)) {
        writer.withBlock(
            "credentialsProvider = object : #T {",
            "}",
            RuntimeTypes.Auth.Credentials.AwsCredentials.CredentialsProvider,
        ) {
            writer.write(
                "override suspend fun resolve(attributes: #1T): #2T = #2T(#3S, #4S)",
                RuntimeTypes.Core.Collections.Attributes,
                RuntimeTypes.Auth.Credentials.AwsCredentials.Credentials,
                "AKID",
                "SECRET",
            )
        }
    }
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
        val signingService = AwsSignatureVersion4Asymmetric.signingServiceName(ctx.model, ctx.service)
        writer.write("#T(#T, #S)", RuntimeTypes.Auth.HttpAuthAws.SigV4AsymmetricAuthScheme, RuntimeTypes.Auth.Signing.AwsSigningStandard.DefaultAwsSigner, signingService)
    }
}

private class SigV4AsymmetricSignedBodyHeaderMiddleware : ProtocolMiddleware {
    override val name: String = "SigV4AsymmetricSignedBodyHeaderMiddleware"

    override fun isEnabledFor(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Boolean {
        val hasEventStream = EventStreamIndex.of(ctx.model).getInputInfo(op).isPresent
        return hasEventStream || op.hasTrait<UnsignedPayloadTrait>()
    }
    override fun render(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
        writer.write(
            "op.context.set(#T.SignedBodyHeader, #T.X_AMZ_CONTENT_SHA256)",
            RuntimeTypes.Auth.Signing.AwsSigningCommon.AwsSigningAttributes,
            RuntimeTypes.Auth.Signing.AwsSigningCommon.AwsSignedBodyHeader,
        )
    }
}

private object SigV4AsymmetricEndpointCustomization : EndpointCustomization {
    override val propertyRenderers: Map<String, EndpointPropertyRenderer> = mapOf(
        "authSchemes" to ::renderSigV4aAuthScheme,
    )
}

private fun renderSigV4aAuthScheme(writer: KotlinWriter, authSchemes: Expression, expressionRenderer: ExpressionRenderer) {
    writer.writeInline("#T to ", RuntimeTypes.SmithyClient.Endpoints.SigningContextAttributeKey)
    writer.withBlock("listOf(", ")") {
        authSchemes.toNode().expectArrayNode().forEach {
            val scheme = it.expectObjectNode()
            val schemeName = scheme.expectStringMember("name").value
            val authFactoryFn = if (schemeName == "sigv4a") RuntimeTypes.Auth.HttpAuthAws.sigV4A else return@forEach

            withBlock("#T(", "),", authFactoryFn) {
                // we delegate back to the expression visitor for each of these fields because it's possible to
                // encounter template strings throughout

                writeInline("serviceName = ")
                renderOrElse(expressionRenderer, scheme.getStringMember("signingName"), "null")

                writeInline("disableDoubleUriEncode = ")
                renderOrElse(expressionRenderer, scheme.getBooleanMember("disableDoubleEncoding"), "false")

                renderSigV4AFields(writer, scheme, expressionRenderer)
            }
        }
    }
}

private fun renderSigV4AFields(writer: KotlinWriter, scheme: ObjectNode, expressionRenderer: ExpressionRenderer) {
    writer.writeInline("signingRegionSet = ")
    expressionRenderer.renderExpression(Expression.fromNode(scheme.expectArrayMember("signingRegionSet")))
    writer.write(",")
}

@SmithyInternalApi
internal fun modelHasSigV4aTrait(ctx: ProtocolGenerator.GenerationContext): Boolean =
    ServiceIndex
        .of(ctx.model)
        .getAuthSchemes(ctx.service)
        .values
        .any { it.javaClass == SigV4ATrait::class.java }

@SmithyInternalApi
internal fun modelHasSigV4aTrait(ctx: CodegenContext): Boolean =
    ServiceIndex
        .of(ctx.model)
        .getAuthSchemes(ctx.settings.service)
        .values
        .any { it.javaClass == SigV4ATrait::class.java }

