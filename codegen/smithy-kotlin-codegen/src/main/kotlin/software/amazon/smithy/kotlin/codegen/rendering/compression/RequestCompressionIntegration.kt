/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

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
import software.amazon.smithy.kotlin.codegen.model.defaultValue
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.model.nonNullable
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
                symbol = RuntimeTypes.SmithyClient.Config.RequestCompressionConfig
                builderSymbol = RuntimeTypes.SmithyClient.Config.RequestCompressionConfig.nestedBuilder.toBuilder()
                    .defaultValue("${this.symbol}.Builder()")
                    .nonNullable()
                    .build()
                toBuilderExpression = ".toBuilder()"
                baseClass = RuntimeTypes.SmithyClient.Config.CompressionClientConfig
                builderBaseClass = RuntimeTypes.SmithyClient.Config.CompressionClientConfig.nestedBuilder
                propertyType = ConfigPropertyType.Custom(
                    render = { prop, writer ->
                        writer.write(
                            "override val #1L: #2T = #2T(builder.#1L)",
                            prop.propertyName,
                            prop.symbol,
                        )
                    },
                )
                documentation = """
                Configuration settings related to request compression.
                See [aws.smithy.kotlin.runtime.client.config.CompressionClientConfig] for more information
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
