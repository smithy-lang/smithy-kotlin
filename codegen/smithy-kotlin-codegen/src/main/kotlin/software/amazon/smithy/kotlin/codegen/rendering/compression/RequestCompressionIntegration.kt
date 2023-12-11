/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.compression

import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolMiddleware
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigProperty
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigPropertyType
import software.amazon.smithy.kotlin.codegen.rendering.util.nestedBuilder
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.traits.RequestCompressionTrait

/**
 * Adds request compression settings if request compression trait is present.
 * Adds middleware that registers interceptor and provides functionality for request compression if request compression
 * is not disabled via settings.
 */
class RequestCompressionIntegration : KotlinIntegration {

    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean =
        model.isTraitApplied(RequestCompressionTrait::class.java)

    override fun customizeMiddleware(
        ctx: ProtocolGenerator.GenerationContext,
        resolved: List<ProtocolMiddleware>,
    ): List<ProtocolMiddleware> = resolved + requestCompressionTraitMiddleware

    override fun additionalServiceConfigProps(ctx: CodegenContext): List<ConfigProperty> =
        listOf(
            ConfigProperty {
                name = "requestCompression"
                symbol = RuntimeTypes.HttpClient.Config.RequestCompressionConfig
                builderSymbol = RuntimeTypes.HttpClient.Config.RequestCompressionConfig.nestedBuilder
                toBuilderExpression = ".toBuilderApplicator()"
                baseClass = RuntimeTypes.HttpClient.Config.CompressionClientConfig
                propertyType = ConfigPropertyType.Custom(
                    render = { prop, writer ->
                        writer.write(
                            "override val #1L: #2T = builder.#1L?.let { #2T(builder.#1L!!) } ?: #2T{}",
                            prop.propertyName,
                            prop.symbol,
                        )
                    },
                )
                documentation = """
                The configuration properties for request compression:
                
                * compressionAlgorithms:
                
                 The list of compression algorithms supported by the SDK.
                 More compression algorithms can be added and may override an existing implementation.
                 Use the `CompressionAlgorithm` interface to create one.
                
                * disableRequestCompression:
                 
                 Flag used to determine when a request should be compressed or not.
                 False by default.
                             
                * requestMinCompressionSizeBytes:
                 
                The threshold in bytes used to determine if a request should be compressed or not.
                MUST be in the range 0-10,485,760 (10 MB). Defaults to 10,240 (10 KB).
                """.trimIndent()
            },
        )
}

private val requestCompressionTraitMiddleware = object : ProtocolMiddleware {
    override val name: String = "RequestCompressionMiddleware"

    override fun isEnabledFor(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Boolean =
        op.hasTrait<RequestCompressionTrait>()

    override fun render(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
        val requestCompressionTrait = op.getTrait<RequestCompressionTrait>()!!
        val supportedCompressionAlgorithms = requestCompressionTrait.encodings

        writer.withBlock(
            "if (config.requestCompression.disableRequestCompression == false) {",
            "}",
        ) {
            withBlock(
                "op.interceptors.add(#T(",
                "))",
                RuntimeTypes.HttpClient.Interceptors.RequestCompressionInterceptor,
            ) {
                write("config.requestCompression.requestMinCompressionSizeBytes,")
                write("config.requestCompression.compressionAlgorithms,")
                write(
                    "listOf(${supportedCompressionAlgorithms.joinToString(
                        separator = ", ",
                        transform = {
                            "\"$it\""
                        },
                    )}),",
                )
            }
        }
    }
}
