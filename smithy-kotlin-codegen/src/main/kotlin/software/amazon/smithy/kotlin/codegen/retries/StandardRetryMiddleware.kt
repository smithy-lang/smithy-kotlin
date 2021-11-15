/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.retries

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolMiddleware
import software.amazon.smithy.model.shapes.OperationShape

/**
 * Adds retry wrappers around operation invocations.
 */
class StandardRetryMiddleware : ProtocolMiddleware {
    companion object {
        const val name: String = "RetryFeature"
    }

    override val name: String = Companion.name

    override fun render(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
        writer.addImport(RuntimeTypes.Http.Middlware.RetryFeature)
        writer.addImport(RuntimeTypes.Core.Retries.Impl.StandardRetryPolicy)

        writer.write(
            "op.install(#T(config.retryStrategy, StandardRetryPolicy.Default))",
            RuntimeTypes.Http.Middlware.RetryFeature
        )
    }
}
