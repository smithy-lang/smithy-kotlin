/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
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
