/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.codegen.aws.customization

import software.amazon.smithy.aws.traits.protocols.AwsQueryCompatibleTrait
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.closeAndOpenBlock
import software.amazon.smithy.kotlin.codegen.core.getContextValue
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.integration.AppendingSectionWriter
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.integration.SectionWriterBinding
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.rendering.protocol.HttpBindingProtocolGenerator.Sections
import software.amazon.smithy.model.Model

/**
 * Registers support for setting AwsQueryCompatible error metadata when deserializing operation errors
 */
class AwsQueryCompatibleErrorDeserialization : KotlinIntegration {
    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean =
        model.expectShape(settings.service).hasTrait<AwsQueryCompatibleTrait>()

    override val sectionWriters: List<SectionWriterBinding>
        get() = listOf(
            SectionWriterBinding(Sections.RenderThrowOperationError.PostErrorDetails, renderQueryErrorDetails),
            SectionWriterBinding(Sections.RenderThrowOperationError.PreExceptionThrow, renderSetErrorMetadata),
        )

    private val renderQueryErrorDetails = AppendingSectionWriter { writer ->
        val exceptionBaseSymbol = writer.getContextValue(Sections.RenderThrowOperationError.PostErrorDetails.ExceptionBaseSymbol)

        writer.write("var queryErrorDetails: #T? = null", RuntimeTypes.AwsProtocolCore.AwsQueryCompatibleErrorDetails)
        writer.withBlock("call.response.headers[#T]?.let {", "}", RuntimeTypes.AwsProtocolCore.XAmznQueryErrorHeader) {
            openBlock("queryErrorDetails = try {")
            write("#T.parse(it)", RuntimeTypes.AwsProtocolCore.AwsQueryCompatibleErrorDetails)
            closeAndOpenBlock("} catch (ex: Exception) {")
            withBlock("""throw #T("Failed to parse awsQuery-compatible error", ex).also {""", "}", exceptionBaseSymbol) {
                write("#T(it, wrappedResponse, errorDetails)", RuntimeTypes.AwsProtocolCore.setAseErrorMetadata)
            }
            closeBlock("}")
        }
        writer.write("")
    }

    private val renderSetErrorMetadata = AppendingSectionWriter { writer ->
        writer.write("queryErrorDetails?.let { #T(ex, it) }", RuntimeTypes.AwsProtocolCore.setAwsQueryCompatibleErrorMetadata)
    }
}
