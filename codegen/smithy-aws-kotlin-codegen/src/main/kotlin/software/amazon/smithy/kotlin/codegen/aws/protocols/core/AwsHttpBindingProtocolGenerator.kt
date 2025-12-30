/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.aws.protocols.core

import software.amazon.smithy.aws.traits.protocols.AwsQueryCompatibleTrait
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.closeAndOpenBlock
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.rendering.protocol.HttpBindingProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator

abstract class AwsHttpBindingProtocolGenerator : HttpBindingProtocolGenerator() {
    override fun operationErrorPostErrorDetailsMiddleware(
        ctx: ProtocolGenerator.GenerationContext,
        writer: KotlinWriter,
        exceptionBaseSymbol: Symbol,
    ) {
        if (ctx.service.hasTrait<AwsQueryCompatibleTrait>()) {
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
    }

    override fun operationErrorPreExceptionThrowMiddleware(
        ctx: ProtocolGenerator.GenerationContext,
        writer: KotlinWriter,
    ) {
        if (ctx.service.hasTrait<AwsQueryCompatibleTrait>()) {
            writer.write("queryErrorDetails?.let { #T(ex, it) }", RuntimeTypes.AwsProtocolCore.setAwsQueryCompatibleErrorMetadata)
        }
    }
}
