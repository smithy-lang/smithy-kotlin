/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.client.*
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.operation.OperationTypeInfo
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.util.Attributes
import kotlin.reflect.KClass

public typealias HttpInterceptor = Interceptor<Any, Any, HttpRequest, HttpResponse>

//  Various contexts for each hook based on available information.
// NOTE: `Output` is a result type and may represent failure
//
// HttpInputInterceptorContext - Input only
// HttpProtocolRequestInterceptorContext - Input + HttpRequest
// HttpProtocolResponseInterceptorContext - Input + HttpRequest + HttpResponse
// HttpAttemptInterceptorContext - Input + HttpRequest + maybe(HttpResponse) + Output
// HttpInputOutputInterceptorContext - Input + HttpRequest + HttpResponse + Output
// HttpFinalProtocolInterceptorContext - Input + maybe(HttpRequest) + maybe(HttpResponse) + Output

private data class HttpInputInterceptorContext<I>(
    override var request: I,
    private val executionContext: ExecutionContext,
) : RequestInterceptorContext<I>, Attributes by executionContext

private data class HttpProtocolRequestInterceptorContext<I>(
    override val request: I,
    override var protocolRequest: HttpRequest,
    private val executionContext: ExecutionContext,
) : ProtocolRequestInterceptorContext<I, HttpRequest>, Attributes by executionContext

private data class HttpProtocolResponseInterceptorContext<I>(
    override val request: I,
    override val protocolRequest: HttpRequest,
    override var protocolResponse: HttpResponse,
    private val executionContext: ExecutionContext,
) : ProtocolResponseInterceptorContext<I, HttpRequest, HttpResponse>, Attributes by executionContext

private data class HttpAttemptInterceptorContext<I, O>(
    override val request: I,
    override var response: Result<O>,
    override val protocolRequest: HttpRequest,
    override val protocolResponse: HttpResponse?,
    private val executionContext: ExecutionContext,
) : ResponseInterceptorContext<I, O, HttpRequest, HttpResponse?>, Attributes by executionContext

private data class HttpInputOutputInterceptorContext<I, O>(
    override val request: I,
    override var response: Result<O>,
    private val call: HttpCall,
    private val executionContext: ExecutionContext,
) : ResponseInterceptorContext<I, O, HttpRequest, HttpResponse>, Attributes by executionContext {

    override val protocolRequest: HttpRequest = call.request
    override val protocolResponse: HttpResponse = call.response
}

private data class HttpFinalInterceptorContext<I, O>(
    override val request: I,
    override var response: Result<O>,
    private val call: HttpCall?,
    private val executionContext: ExecutionContext,
) : ResponseInterceptorContext<I, O, HttpRequest?, HttpResponse?>, Attributes by executionContext {

    override val protocolRequest: HttpRequest? = call?.request
    override val protocolResponse: HttpResponse? = call?.response
}

// FIXME - investigate propagating Any as upper bounds for SdkHttpOperation <I,O> generics
// FIXME - don't love this type name
// FIXME - kdoc
internal class InterceptorExecutor<I, O>(
    private val execContext: ExecutionContext,
    private val interceptors: List<HttpInterceptor>,
    private val typeInfo: OperationTypeInfo,
) {

    // this changes as execution progresses, it represents the most up-to-date version of the operation input
    // if we begin executing an operation at all it is guaranteed to exist because `readBeforeExecution` is the first
    // thing invoked when executing a request
    private var _lastInput: I? = null

    private fun <T> checkType(phase: String, expected: KClass<*>, actual: Any): T {
        check(expected.isInstance(actual)) { "$phase invalid type conversion: found ${actual::class}; expected $expected" }
        @Suppress("UNCHECKED_CAST")
        return actual as T
    }
    private fun <T> checkResultType(phase: String, result: Result<Any>, expected: KClass<*>): Result<T> =
        result.map { checkType(phase, expected, it) }

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

            curr.fold({ prev }, {
                if (prev.isFailure) {
                    it.addSuppressed(prev.exceptionOrNull()!!)
                }
                Result.failure(it)
            },)
        }

    fun readBeforeExecution(input: I): Result<Unit> {
        _lastInput = input
        val context = HttpInputInterceptorContext(input as Any, execContext)
        return executeAll { interceptor ->
            interceptor.readBeforeExecution(context)
        }
    }

    fun modifyBeforeSerialization(input: I): I {
        val context = HttpInputInterceptorContext(input as Any, execContext)
        val modified = interceptors.fold(context) { ctx, interceptor ->
            val modified = interceptor.modifyBeforeSerialization(ctx)
            checkType<I>("modifyBeforeSerialization", typeInfo.inputType, modified)
            context.request = modified
            ctx
        }.request

        @Suppress("UNCHECKED_CAST")
        return modified as I
    }

    fun readBeforeSerialization(input: I) {
        // FIXME how important is it that modifyBeforeCompletion sees partial results (e.g. from modifyBeforeSerialization)?
        _lastInput = input
        val context = HttpInputInterceptorContext(input as Any, execContext)
        interceptors.forEach { it.readBeforeSerialization(context) }
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

    private inline fun modifyHttpRequestHook(
        request: HttpRequest,
        block: (HttpInterceptor, context: HttpProtocolRequestInterceptorContext<Any>) -> HttpRequest,
    ): HttpRequest {
        val input = checkNotNull(_lastInput)
        val context = HttpProtocolRequestInterceptorContext(input as Any, request, execContext)
        val modified = runCatching {
            interceptors.fold(context) { ctx, interceptor ->
                context.protocolRequest = block(interceptor, context)
                ctx
            }
        }
        return modified.getOrThrow().protocolRequest
    }

    fun readAfterSerialization(request: HttpRequest) = readHttpHook(request) {
            interceptor, context ->
        interceptor.readAfterSerialization(context)
    }

    fun modifyBeforeRetryLoop(request: HttpRequest): HttpRequest =
        modifyHttpRequestHook(request) { interceptor, context ->
            interceptor.modifyBeforeRetryLoop(context)
        }

    fun readBeforeAttempt(request: HttpRequest) =
        readHttpHook(request) { interceptor, context ->
            interceptor.readBeforeAttempt(context)
        }

    fun modifyBeforeSigning(request: HttpRequest): HttpRequest =
        modifyHttpRequestHook(request) { interceptor, context ->
            interceptor.modifyBeforeSigning(context)
        }

    fun readBeforeSigning(request: HttpRequest) = readHttpHook(request) { interceptor, context ->
        interceptor.readBeforeSigning(context)
    }

    fun readAfterSigning(request: HttpRequest) = readHttpHook(request) { interceptor, context ->
        interceptor.readAfterSigning(context)
    }

    fun modifyBeforeTransmit(request: HttpRequest): HttpRequest =
        modifyHttpRequestHook(request) { interceptor, context ->
            interceptor.modifyBeforeTransmit(context)
        }

    fun readBeforeTransmit(request: HttpRequest) = readHttpHook(request) { interceptor, context ->
        interceptor.readBeforeTransmit(context)
    }

    fun readAfterTransmit(call: HttpCall) =
        readHttpHook(call.request, call.response) { interceptor, context ->
            interceptor.readAfterTransmit(context)
        }

    fun modifyBeforeDeserialization(call: HttpCall): HttpResponse {
        val input = checkNotNull(_lastInput)
        val context = HttpProtocolResponseInterceptorContext(input as Any, call.request, call.response, execContext)
        val modified = runCatching {
            interceptors.fold(context) { ctx, interceptor ->
                context.protocolResponse = interceptor.modifyBeforeDeserialization(context)
                ctx
            }
        }
        return modified.getOrThrow().protocolResponse
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

    fun modifyBeforeAttemptCompletion(result: Result<O>, httpRequest: HttpRequest, httpResponse: HttpResponse?): Result<O> {
        val input = checkNotNull(_lastInput)

        @Suppress("UNCHECKED_CAST")
        val context = HttpAttemptInterceptorContext(input as Any, result as Result<Any>, httpRequest, httpResponse, execContext)

        val modified = interceptors.fold(context) { ctx, interceptor ->
            val modified = interceptor.modifyBeforeAttemptCompletion(ctx)
            checkResultType<O>("modifyBeforeAttemptCompletion", modified, typeInfo.outputType)
            ctx.response = modified
            ctx
        }.response

        return checkResultType("modifyBeforeAttemptCompletion", modified, typeInfo.outputType)
    }

    fun readAfterAttempt(result: Result<O>, httpRequest: HttpRequest, httpResponse: HttpResponse?) {
        val input = checkNotNull(_lastInput)

        @Suppress("UNCHECKED_CAST")
        val context = HttpAttemptInterceptorContext(input as Any, result as Result<Any>, httpRequest, httpResponse, execContext)
        val readResult = executeAll { interceptor ->
            interceptor.readAfterAttempt(context)
        }

        return readResult.getOrThrow()
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

        val modifyResult = runCatching {
            interceptors.fold(context) { ctx, interceptor ->
                val modified = interceptor.modifyBeforeCompletion(ctx)
                checkResultType<O>("modifyBeforeCompletion", modified, typeInfo.outputType)
                ctx.response = modified
                ctx
            }.response
        }

        return modifyResult.fold(
            { checkResultType("modifyBeforeCompletion", it, typeInfo.outputType) },
            { Result.failure(it) },
        )
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
