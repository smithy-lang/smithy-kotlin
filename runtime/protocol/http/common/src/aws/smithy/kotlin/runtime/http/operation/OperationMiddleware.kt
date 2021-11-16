/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.io.middleware.Middleware
import aws.smithy.kotlin.runtime.io.middleware.ModifyRequest

/**
 * Middleware that intercepts the [SdkOperationExecution.initialize] phase
 */
interface InitializeMiddleware<Request, Response> : Middleware<OperationRequest<Request>, Response> {
    fun install(op: SdkHttpOperation<Request, Response>) {
        op.execution.initialize.register(this)
    }
}

/**
 * Middleware that intercepts the [SdkOperationExecution.mutate] phase
 */
interface MutateMiddleware<Response> : Middleware<SdkHttpRequest, Response> {
    fun install(op: SdkHttpOperation<*, Response>) {
        op.execution.mutate.register(this)
    }
}

/**
 * A middleware that only mutates the outgoing [SdkHttpRequest].
 *
 * NOTE: This can be applied to any phase that uses [SdkHttpRequest] as it's input type (e.g. mutate, finalize, receive)
 */
interface ModifyRequestMiddleware : ModifyRequest<SdkHttpRequest> {
    /**
     * Register this transform with the operation's execution
     *
     * NOTE: the default implementation will register with the [SdkOperationExecution.mutate] phase.
     */
    fun install(op: SdkHttpOperation<*, *>) {
        op.execution.mutate.register(this)
    }
}

/**
 * Middleware that intercepts the [SdkOperationExecution.finalize] phase
 */
interface FinalizeMiddleware<Response> : Middleware<SdkHttpRequest, Response> {
    fun install(op: SdkHttpOperation<*, Response>) {
        op.execution.finalize.register(this)
    }
}

/**
 * Middleware that intercepts the [SdkOperationExecution.receive] phase
 */
interface ReceiveMiddleware : Middleware<SdkHttpRequest, HttpCall> {
    fun install(op: SdkHttpOperation<*, *>) {
        op.execution.receive.register(this)
    }
}

/**
 * A middleware that directly registers interceptors onto an operation inline in install.
 * This can be useful for example if a middleware needs to hook into multiple phases:
 *
 * ```
 * class MyMiddleware<I, O> : InlineMiddleware<I, O> {
 *     override fun install(op: SdkHttpOperation<I, O>) {
 *         op.execution.initialize.intercept { req, next -> ... }
 *
 *         op.execution.mutate.intercept { req, next -> ... }
 *     }
 * }
 *
 * ```
 */
interface InlineMiddleware<I, O> {
    fun install(op: SdkHttpOperation<I, O>)
}
