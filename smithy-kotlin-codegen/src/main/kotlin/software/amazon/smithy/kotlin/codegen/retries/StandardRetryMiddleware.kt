/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.retries

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.rendering.protocol.HttpFeatureMiddleware

/**
 * Adds retry wrappers around operation invocations.
 */
class StandardRetryMiddleware : HttpFeatureMiddleware() {

    override val name: String = RuntimeTypes.Http.Middlware.Retry.name

    override fun renderConfigure(writer: KotlinWriter) {
        writer.addImport(RuntimeTypes.Http.Middlware.Retry)
        writer.addImport(RuntimeTypes.Core.Retries.Impl.StandardRetryPolicy)

        writer.write("strategy = config.retryStrategy")
        writer.write("policy = StandardRetryPolicy.Default")
    }
}
