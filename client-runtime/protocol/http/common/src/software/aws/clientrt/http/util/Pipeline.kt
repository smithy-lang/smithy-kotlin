/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.util

import io.ktor.util.pipeline.PipelineContext
import io.ktor.util.pipeline.PipelinePhase
import io.ktor.util.pipeline.Pipeline as KtorPipeline

typealias PipelineFuncInterceptor<TSubject, TContext> = suspend PipelineContext<TSubject, TContext>.() -> Unit
typealias Phase = PipelinePhase
typealias Pipeline<TSubject, TContext> = KtorPipeline<TSubject, TContext>

/**
 * Extension to intercept using a free function rather than a lambda with receiver
 */
fun <TSubject : Any, TContext : Any> Pipeline<TSubject, TContext>.interceptFn(phase: PipelinePhase, block: PipelineFuncInterceptor<TSubject, TContext>) {
    intercept(phase) {
        block(this)
    }
}