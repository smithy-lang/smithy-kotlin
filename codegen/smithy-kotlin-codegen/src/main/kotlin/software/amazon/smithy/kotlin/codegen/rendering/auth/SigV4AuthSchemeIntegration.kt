/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.auth

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
import software.amazon.smithy.kotlin.codegen.model.knowledge.AwsSignatureVersion4
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.EndpointCustomization
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.EndpointPropertyRenderer
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.ExpressionRenderer
import software.amazon.smithy.kotlin.codegen.rendering.protocol.*
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigProperty
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigPropertyType
import software.amazon.smithy.kotlin.codegen.utils.dq
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.EventStreamIndex
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.rulesengine.language.syntax.expressions.Expression
import java.util.*

/**
 * Register support for the `aws.auth#sigv4` auth scheme.
 */
open class SigV4AuthSchemeIntegration : KotlinIntegration {
    // Allow integrations to customize the service config props, later integrations take precedence
    // Needs to happen after the `SigV4AsymmetricTraitCustomization` (-60).
    override val order: Byte = -50

    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean =
        AwsSignatureVersion4.isSupportedAuthentication(model, settings.getService(model))

    override fun authSchemes(ctx: ProtocolGenerator.GenerationContext): List<AuthSchemeHandler> = listOf(SigV4AuthSchemeHandler())

    override fun customizeMiddleware(
        ctx: ProtocolGenerator.GenerationContext,
        resolved: List<ProtocolMiddleware>,
    ): List<ProtocolMiddleware> = resolved + Sigv4SignedBodyHeaderMiddleware()

    override fun additionalServiceConfigProps(ctx: CodegenContext): List<ConfigProperty> = listOf(credentialsProviderProp)

    override fun customizeEndpointResolution(ctx: ProtocolGenerator.GenerationContext): EndpointCustomization =
        Sigv4EndpointCustomization

    override val sectionWriters: List<SectionWriterBinding>
        get() = listOf(
            SectionWriterBinding(HttpProtocolUnitTestRequestGenerator.ConfigureServiceClient, renderHttpProtocolRequestTestConfigureServiceClient),
            SectionWriterBinding(HttpProtocolUnitTestResponseGenerator.ConfigureServiceClient, renderHttpProtocolResponseTestConfigureServiceClient),
            SectionWriterBinding(HttpProtocolClientGenerator.ClientInitializer, renderClientInitializer),
            SectionWriterBinding(HttpProtocolClientGenerator.MergeServiceDefaults, renderMergeServiceDefaults),
        )

    // set service client defaults for HTTP request protocol tests
    private val renderHttpProtocolRequestTestConfigureServiceClient = AppendingSectionWriter { writer ->
        val ctx = writer.getContextValue(HttpProtocolUnitTestRequestGenerator.ConfigureServiceClient.Context)
        val op = writer.getContextValue(HttpProtocolUnitTestRequestGenerator.ConfigureServiceClient.Operation)
        renderConfigureServiceClientForTest(ctx, op, writer)
    }

    // set service client defaults for HTTP response protocol tests
    private val renderHttpProtocolResponseTestConfigureServiceClient = AppendingSectionWriter { writer ->
        val ctx = writer.getContextValue(HttpProtocolUnitTestResponseGenerator.ConfigureServiceClient.Context)
        val op = writer.getContextValue(HttpProtocolUnitTestResponseGenerator.ConfigureServiceClient.Operation)
        renderConfigureServiceClientForTest(ctx, op, writer)
    }

    // add credentials to managed resources in the service client initializer
    private val renderClientInitializer = AppendingSectionWriter { writer ->
        val ctx = writer.getContextValue(HttpProtocolClientGenerator.ClientInitializer.GenerationContext)
        if (AwsSignatureVersion4.isSupportedAuthentication(ctx.model, ctx.settings.getService(ctx.model))) {
            writer.write("managedResources.#T(config.credentialsProvider)", RuntimeTypes.Core.IO.addIfManaged)
        }
    }

    // render sigv4 related execution context properties
    private val renderMergeServiceDefaults = AppendingSectionWriter { writer ->
        val ctx = writer.getContextValue(HttpProtocolClientGenerator.ClientInitializer.GenerationContext)
        // fill in auth/signing attributes
        if (AwsSignatureVersion4.isSupportedAuthentication(ctx.model, ctx.service)) {
            // default signing context (most of this has been moved to auth schemes but some things like event streams still depend on this)
            val signingServiceName = AwsSignatureVersion4.signingServiceName(ctx.service)
            writer.putIfAbsent(
                RuntimeTypes.Auth.Signing.AwsSigningCommon.AwsSigningAttributes,
                "SigningService",
                signingServiceName.dq(),
            )
            writer.putIfAbsent(RuntimeTypes.Auth.Signing.AwsSigningCommon.AwsSigningAttributes, "CredentialsProvider")
        }
    }

    private fun renderConfigureServiceClientForTest(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
        if (AwsSignatureVersion4.hasSigV4AuthScheme(ctx.model, ctx.service, op)) {
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
}

open class SigV4AuthSchemeHandler : AuthSchemeHandler {
    override val authSchemeId: ShapeId = SigV4Trait.ID

    override val authSchemeIdSymbol: Symbol = buildSymbol {
        name = "AuthSchemeId.AwsSigV4"
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
        writer.write(expr, RuntimeTypes.Auth.HttpAuthAws.sigV4)
    }

    override fun instantiateAuthSchemeExpr(ctx: ProtocolGenerator.GenerationContext, writer: KotlinWriter) {
        val signingService = AwsSignatureVersion4.signingServiceName(ctx.service)
        writer.write("#T(#T, #S)", RuntimeTypes.Auth.HttpAuthAws.SigV4AuthScheme, RuntimeTypes.Auth.Signing.AwsSigningStandard.DefaultAwsSigner, signingService)
    }
}

/**
 * Conditionally updates the operation context to set the signed body header attribute
 * e.g. to set `X-Amz-Content-Sha256` header.
 */
internal class Sigv4SignedBodyHeaderMiddleware : ProtocolMiddleware {
    override val name: String = "Sigv4SignedBodyHeaderMiddleware"

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

private object Sigv4EndpointCustomization : EndpointCustomization {
    override val propertyRenderers: Map<String, EndpointPropertyRenderer> = mapOf(
        "authSchemes" to ::renderAuthSchemes,
    )
}

// SigV4a requires SigV4 so SigV4 integration renders SigV4a auth scheme.
// See comment in example model: https://smithy.io/2.0/aws/aws-auth.html?highlight=sigv4#aws-auth-sigv4a-trait
private fun renderAuthSchemes(writer: KotlinWriter, authSchemes: Expression, expressionRenderer: ExpressionRenderer) {
    writer.writeInline("#T to ", RuntimeTypes.SmithyClient.Endpoints.SigningContextAttributeKey)
    writer.withBlock("listOf(", ")") {
        authSchemes.toNode().expectArrayNode().forEach {
            val scheme = it.expectObjectNode()
            val schemeName = scheme.expectStringMember("name").value

            val authFactoryFn = when (schemeName) {
                "sigv4" -> RuntimeTypes.Auth.HttpAuthAws.sigV4
                "sigv4a" -> RuntimeTypes.Auth.HttpAuthAws.sigV4A
                else -> return@forEach
            }

            withBlock("#T(", "),", authFactoryFn) {
                // we delegate back to the expression visitor for each of these fields because it's possible to
                // encounter template strings throughout

                writeInline("serviceName = ")
                renderOrElse(expressionRenderer, scheme.getStringMember("signingName"), "null")

                writeInline("disableDoubleUriEncode = ")
                renderOrElse(expressionRenderer, scheme.getBooleanMember("disableDoubleEncoding"), "false")

                renderFieldsForScheme(writer, scheme, expressionRenderer)
            }
        }
    }
}

private fun renderFieldsForScheme(writer: KotlinWriter, scheme: ObjectNode, expressionRenderer: ExpressionRenderer) {
    when (scheme.expectStringMember("name").value) {
        "sigv4" -> renderSigV4Fields(writer, scheme, expressionRenderer)
        "sigv4a" -> renderSigV4AFields(writer, scheme, expressionRenderer)
    }
}

private fun renderSigV4Fields(writer: KotlinWriter, scheme: ObjectNode, expressionRenderer: ExpressionRenderer) {
    writer.writeInline("signingRegion = ")
    writer.renderOrElse(expressionRenderer, scheme.getStringMember("signingRegion"), "null")
}

private fun renderSigV4AFields(writer: KotlinWriter, scheme: ObjectNode, expressionRenderer: ExpressionRenderer) {
    writer.writeInline("signingRegionSet = ")
    expressionRenderer.renderExpression(Expression.fromNode(scheme.expectArrayMember("signingRegionSet")))
    writer.write(",")
}

private fun KotlinWriter.renderOrElse(
    expressionRenderer: ExpressionRenderer,
    optionalNode: Optional<out Node>,
    whenNullValue: String,
) {
    val nullableNode = optionalNode.getOrNull()
    when (nullableNode) {
        null -> writeInline(whenNullValue)
        else -> expressionRenderer.renderExpression(Expression.fromNode(nullableNode))
    }
    write(",")
}

internal val credentialsProviderProp = ConfigProperty {
    symbol = RuntimeTypes.Auth.Credentials.AwsCredentials.CredentialsProvider
    baseClass = RuntimeTypes.Auth.Credentials.AwsCredentials.CredentialsProviderConfig
    useNestedBuilderBaseClass()
    documentation = """
                The AWS credentials provider to use for authenticating requests. 
                NOTE: The caller is responsible for managing the lifetime of the provider when set. The SDK
                client will not close it when the client is closed.
    """.trimIndent()

    propertyType = ConfigPropertyType.Required()
}
