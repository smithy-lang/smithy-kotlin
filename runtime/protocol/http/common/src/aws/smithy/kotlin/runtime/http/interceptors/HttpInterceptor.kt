/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.client.*
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.util.Attributes

public typealias HttpInterceptor = Interceptor<Any, Any, HttpRequest, HttpResponse>

//  Various contexts for each hook based on available information
//
// HttpInputInterceptorContext - Input only
// HttpProtocolRequestInterceptorContext - Input + HttpRequest
// HttpProtocolResponseInterceptorContext - Input + HttpRequest + HttpResponse
// HttpInputOutputInterceptorContext - Input + HttpRequest + HttpResponse + Output
// HttpFinalProtocolInterceptorContext - Input + maybe(HttpRequest) + maybe(HttpResponse) + Output

private data class HttpInputInterceptorContext<I>(
    override var request: I,
    private val executionContext: ExecutionContext,
) : RequestInterceptorContext<I>, Attributes by executionContext

private data class HttpProtocolRequestInterceptorContext<I>(
    override var request: I,
    override val protocolRequest: HttpRequest,
    private val executionContext: ExecutionContext,
) : ProtocolRequestInterceptorContext<I, HttpRequest>, Attributes by executionContext

private data class HttpProtocolResponseInterceptorContext<I>(
    override var request: I,
    override val protocolRequest: HttpRequest,
    override val protocolResponse: HttpResponse,
    private val executionContext: ExecutionContext,
) : ProtocolResponseInterceptorContext<I, HttpRequest, HttpResponse>, Attributes by executionContext

private data class HttpInputOutputInterceptorContext<I, O>(
    override var request: I,
    override var response: Result<O>,
    private val call: HttpCall,
    private val executionContext: ExecutionContext,
) : ResponseInterceptorContext<I, O, HttpRequest, HttpResponse>, Attributes by executionContext {

    override val protocolRequest: HttpRequest = call.request
    override val protocolResponse: HttpResponse = call.response
}

private data class HttpFinalInterceptorContext<I, O>(
    override var request: I,
    override var response: Result<O>,
    private val call: HttpCall?,
    private val executionContext: ExecutionContext,
) : ResponseInterceptorContext<I, O, HttpRequest?, HttpResponse?>, Attributes by executionContext {

    override val protocolRequest: HttpRequest? = call?.request
    override val protocolResponse: HttpResponse? = call?.response
}

// FIXME - propagate `Any` bounds to I and O generics to remove unchecked casts to Result<Any>.
//         Input and output can never be null but this will require changing a bunch of generics elsewhere to
//         add similar bounds.
// FIXME - don't love this type name
internal class InterceptorExecutor<I, O>(
    private val execContext: ExecutionContext,
    private val interceptors: List<HttpInterceptor>,
) {

    // this changes as execution progresses, it represents the most up-to-date version of the operation input
    private var _lastInput: I? = null

    private fun <T> assertType(phase: String, result: Result<Any>): Result<T> {
        @Suppress("UNCHECKED_CAST")
        return result as? Result<T> ?: error("$phase invalid type conversion: found ${result::class}")
    }

    /**
     * Execute all interceptors and return a [Result]. If the result is success then
     * every interceptor executed without failure. If any interceptor threw an exception
     * the result will contain the most recent failure (with all previous failures added
     * as suppressed exceptions).
     */
    private inline fun executeAll(block: (HttpInterceptor) -> Unit): Result<Unit> =
        interceptors.fold(Result.success(Unit)) { prev, interceptor ->
            val curr = runCatching {
                block(interceptor)
            }

            if (prev.isFailure) {
                val prevEx = prev.exceptionOrNull()!!
                curr.exceptionOrNull()?.addSuppressed(prevEx)
            }
            curr
        }

    fun readBeforeExecution(input: I): Result<I> {
        _lastInput = input
        val context = HttpInputInterceptorContext(input as Any, execContext)
        val readResult = executeAll { interceptor ->
            interceptor.readBeforeExecution(context)
        }
        readResult.getOrThrow()
        return Result.success(input)
    }

    fun modifyBeforeSerialization(input: I): Result<I> {
        val context = HttpInputInterceptorContext(input as Any, execContext)
        val modified = runCatching {
            interceptors.fold(context) { ctx, interceptor ->
                context.request = interceptor.modifyBeforeSerialization(ctx)
                ctx
            }.request
        }

        return assertType("modifyBeforeSerialization", modified)
    }

    fun readBeforeSerialization(input: I) {
        // FIXME how important is it that modifyBeforeCompletion sees partial results (e.g. from modifyBeforeSerialization)?
        _lastInput = input
        val context = HttpInputInterceptorContext(input as Any, execContext)
        interceptors.forEach { it.readBeforeExecution(context) }
    }

    private inline fun readHttpHook(
        request: HttpRequest,
        block: (HttpInterceptor, context: HttpProtocolRequestInterceptorContext<Any>) -> Unit,
    ) {
        val input = checkNotNull(_lastInput)
        val context = HttpProtocolRequestInterceptorContext(input as Any, request, execContext)
        interceptors.forEach { block(it, context) }
    }
    private inline fun readHttpHook(
        request: HttpRequest,
        response: HttpResponse,
        block: (HttpInterceptor, context: HttpProtocolResponseInterceptorContext<Any>) -> Unit,
    ) {
        val input = checkNotNull(_lastInput)
        val context = HttpProtocolResponseInterceptorContext(input as Any, request, response, execContext)
        interceptors.forEach { block(it, context) }
    }

    fun readAfterSerialization(request: HttpRequest) = readHttpHook(request) {
            interceptor, context ->
        interceptor.readAfterSerialization(context)
    }

    fun modifyBeforeRetryLoop(): HttpRequest {
        TODO()
    }

    fun readBeforeAttempt(request: HttpRequest) = readHttpHook(request) {
            interceptor, context ->
        interceptor.readBeforeAttempt(context)
    }

    fun modifyBeforeSigning(): HttpRequest {
        TODO()
    }

    fun readBeforeSigning(request: HttpRequest) = readHttpHook(request) { interceptor, context ->
        interceptor.readBeforeSigning(context)
    }

    fun readAfterSigning(request: HttpRequest) = readHttpHook(request) { interceptor, context ->
        interceptor.readAfterSigning(context)
    }

    fun modifyBeforeTransmit(request: HttpRequest): HttpRequest {
        TODO()
    }

    fun readBeforeTransmit(request: HttpRequest) = readHttpHook(request) { interceptor, context ->
        interceptor.readBeforeTransmit(context)
    }

    fun readAfterTransmit(call: HttpCall) =
        readHttpHook(call.request, call.response) { interceptor, context ->
            interceptor.readAfterTransmit(context)
        }

    fun modifyBeforeDeserialization() {
        TODO()
    }

    fun readBeforeDeserialization(call: HttpCall) =
        readHttpHook(call.request, call.response) { interceptor, context ->
            interceptor.readBeforeDeserialization(context)
        }

    fun readAfterDeserialization(output: O, call: HttpCall) {
        val input = checkNotNull(_lastInput)
        val response = Result.success(output as Any)
        val context = HttpInputOutputInterceptorContext(input as Any, response, call, execContext)
        interceptors.forEach { interceptor -> interceptor.readAfterDeserialization(context) }
    }

    fun modifyBeforeAttemptCompletion() {
        TODO()
    }

    fun readAfterAttempt() {
        TODO()
    }

    fun modifyBeforeCompletion(result: Result<O>): Result<O> {
        // FIXME - this is technically wrong because call implies we actually got a protocol response so a failure in any interceptor/middleware prior
        // to getting a response would not have InterceptorContext.protocolRequest set. I'm not sure how much we care about that or not...what would you do with a request that
        // wasn't actually sent in this final hook?
        val lastCall = execContext.getOrNull(HttpOperationContext.HttpCallList)?.last()

        // SAFETY: If we started executing an operation at all the input will be set at least once
        val input = checkNotNull(_lastInput)

        @Suppress("UNCHECKED_CAST")
        val context = HttpFinalInterceptorContext(input as Any, result as Result<Any>, lastCall, execContext)

        return runCatching {
            val modified = interceptors.fold(context) { ctx, interceptor ->
                context.response = interceptor.modifyBeforeCompletion(ctx)
                ctx
            }.response

            assertType<O>("modifyBeforeCompletion", modified)
        }.getOrThrow()
    }

    fun readAfterExecution(result: Result<O>) {
        // FIXME - this is technically wrong because call implies we actually got a protocol response so a failure in any interceptor/middleware prior
        // to getting a response would not have InterceptorContext.protocolRequest set. I'm not sure how much we care about that or not...what would you do with a request that
        // wasn't actually sent in this final hook?
        val lastCall = execContext.getOrNull(HttpOperationContext.HttpCallList)?.last()

        // SAFETY: If we started executing an operation at all input will be set at least once
        val input = checkNotNull(_lastInput)

        @Suppress("UNCHECKED_CAST")
        val context = HttpFinalInterceptorContext(input as Any, result as Result<Any>, lastCall, execContext)
        val readResult = executeAll { interceptor ->
            interceptor.readAfterExecution(context)
        }

        // if an error was encountered it's going to be the result of the operation
        readResult.getOrThrow()
    }
}
