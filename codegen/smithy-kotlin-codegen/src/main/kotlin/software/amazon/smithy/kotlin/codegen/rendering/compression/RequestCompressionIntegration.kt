/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.compression

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.model.defaultValue
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolMiddleware
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigProperty
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
                name = "compressionAlgorithms"
                symbol = Symbol.builder()
                    .name("List<${RuntimeTypes.Core.Http.Compression.CompressionAlgorithm}>")
                    .defaultValue("listOf(${RuntimeTypes.Core.Http.Compression.Gzip}())")
                    .build()
                baseClass = RuntimeTypes.HttpClient.Config.CompressionClientConfig
                useNestedBuilderBaseClass()
                documentation = """
                The list of compression algorithms supported by the SDK.
                More compression algorithms can be added and may override an existing implementation.
                Use the `CompressionAlgorithm` interface to create one.
                """.trimIndent()
            },
            ConfigProperty {
                name = "disableRequestCompression"
                useSymbolWithNullableBuilder(KotlinTypes.Boolean, "false")
                baseClass = RuntimeTypes.HttpClient.Config.CompressionClientConfig
                useNestedBuilderBaseClass()
                documentation = """
                    Flag used to determine if a request should be compressed or not.
                    False by default.
                """.trimIndent()
            },
            ConfigProperty {
                name = "requestMinCompressionSizeBytes"
                useSymbolWithNullableBuilder(KotlinTypes.Long, "10_240")
                baseClass = RuntimeTypes.HttpClient.Config.CompressionClientConfig
                useNestedBuilderBaseClass()
                documentation = """
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
            "if (config.disableRequestCompression == false) {",
            "}",
        ) {
            withBlock(
                "op.interceptors.add(#T(",
                "))",
                RuntimeTypes.HttpClient.Interceptors.RequestCompressionInterceptor,
            ) {
                write("config.requestMinCompressionSizeBytes,")
                write("config.compressionAlgorithms,")
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
