/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.protocol

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.model.shapes.OperationShape

/**
 * General purpose middleware that allows mutation of HTTP headers
 */
class MutateHeadersMiddleware(
    private val extraHeaders: Map<String, String> = emptyMap(),
    private val overrideHeaders: Map<String, String> = emptyMap(),
    private val addMissingHeaders: Map<String, String> = emptyMap(),
) : ProtocolMiddleware {
    override val name: String = "MutateHeaders"
    override val order: Byte = 10
    override fun render(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
        writer.addImport(RuntimeTypes.HttpClient.Middleware.MutateHeadersMiddleware)
            .withBlock("op.install(", ")") {
                withBlock("#T().apply {", "}", RuntimeTypes.HttpClient.Middleware.MutateHeadersMiddleware) {
                    overrideHeaders.forEach {
                        writer.write("set(#S, #S)", it.key, it.value)
                    }
                    extraHeaders.forEach {
                        writer.write("append(#S, #S)", it.key, it.value)
                    }
                    addMissingHeaders.forEach {
                        writer.write("setIfMissing(#S, #S)", it.key, it.value)
                    }
                }
            }
    }
}
