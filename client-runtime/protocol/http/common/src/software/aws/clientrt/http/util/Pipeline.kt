/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.http.util

import io.ktor.util.pipeline.Pipeline as KtorPipeline
import io.ktor.util.pipeline.PipelineContext
import io.ktor.util.pipeline.PipelinePhase

typealias PipelineFuncInterceptor<TSubject, TContext> = suspend PipelineContext<TSubject, TContext>.() -> Unit
typealias Phase = PipelinePhase

/**
 *
 */
open class Pipeline<TSubject : Any, TContext : Any>(vararg phases: PipelinePhase) : KtorPipeline<TSubject, TContext>(*phases) {

    /**
     * Extension to intercept using a free function rather than a lambda with receiver
     */
    fun interceptFn(phase: PipelinePhase, block: PipelineFuncInterceptor<TSubject, TContext>) {
        intercept(phase) {
            block(this)
        }
    }
}
