/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.client.*
import aws.smithy.kotlin.runtime.http.operation.OperationTypeInfo
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import kotlin.reflect.KClass

// Various contexts for each hook based on available information.
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
    override val executionContext: ExecutionContext,
) : RequestInterceptorContext<I>

private data class HttpProtocolRequestInterceptorContext<I>(
    override val request: I,
    override var protocolRequest: HttpRequest,
    override val executionContext: ExecutionContext,
) : ProtocolRequestInterceptorContext<I, HttpRequest>

private data class HttpProtocolResponseInterceptorContext<I>(
    override val request: I,
    override val protocolRequest: HttpRequest,
    override var protocolResponse: HttpResponse,
    override val executionContext: ExecutionContext,
) : ProtocolResponseInterceptorContext<I, HttpRequest, HttpResponse>

private data class HttpAttemptInterceptorContext<I, O>(
    override val request: I,
    override var response: Result<O>,
    override val protocolRequest: HttpRequest,
    override val protocolResponse: HttpResponse?,
    override val executionContext: ExecutionContext,
) : ResponseInterceptorContext<I, O, HttpRequest, HttpResponse?>

private data class HttpInputOutputInterceptorContext<I, O>(
    override val request: I,
    override var response: Result<O>,
    private val call: HttpCall,
    override val executionContext: ExecutionContext,
) : ResponseInterceptorContext<I, O, HttpRequest, HttpResponse> {

    override val protocolRequest: HttpRequest = call.request
    override val protocolResponse: HttpResponse = call.response
}

private data class HttpFinalInterceptorContext<I, O>(
    override val request: I,
    override var response: Result<O>,
    override val protocolRequest: HttpRequest?,
    override val protocolResponse: HttpResponse?,
    override val executionContext: ExecutionContext,
) : ResponseInterceptorContext<I, O, HttpRequest?, HttpResponse?>

// TODO - investigate propagating Any as upper bounds for SdkHttpOperation <I,O> generics
/**
 * Fan-out facade over raw interceptors that adapts the internal view of an operation execution to the public facing
 * view interceptor hooks see.
 *
 * @param execContext the operation [ExecutionContext]
 * @param interceptors the list of interceptors to execute
 * @param typeInfo the expected input/output type info used to keep hook implementations honest
 */
internal class InterceptorExecutor<I, O>(
    private val execContext: ExecutionContext,
    private val interceptors: List<HttpInterceptor>,
    private val typeInfo: OperationTypeInfo,
) {

    // This changes as execution progresses, it represents the most up-to-date version of the operation input.
    // If we begin executing an operation at all it is guaranteed to exist because `readBeforeExecution` is the first
    // thing invoked when executing a request
    private var _lastInput: I? = null

    // Track most up to date http request and response. The final two hooks do not have easy access to this data
    // so we store it as execution progresses
    private var _lastHttpRequest: HttpRequest? = null
    private var _lastHttpResponse: HttpResponse? = null

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

            curr.fold({ prev }, { currEx ->
                prev.exceptionOrNull()?.let { currEx.addSuppressed(it) }
                Result.failure(currEx)
            })
        }

    fun readBeforeExecution(input: I): Result<Unit> {
        _lastInput = input
        val context = HttpInputInterceptorContext(input as Any, execContext)
        return executeAll { interceptor ->
            interceptor.readBeforeExecution(context)
        }
    }

    suspend fun modifyBeforeSerialization(input: I): I {
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
        _lastInput = input
        val context = HttpInputInterceptorContext(input as Any, execContext)
        interceptors.forEach { it.readBeforeSerialization(context) }
    }

    private inline fun readHttpHook(
        request: HttpRequest,
        block: (HttpInterceptor, context: HttpProtocolRequestInterceptorContext<Any>) -> Unit,
    ) {
        _lastHttpRequest = request
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

    suspend fun modifyBeforeRetryLoop(request: HttpRequest): HttpRequest =
        modifyHttpRequestHook(request) { interceptor, context ->
            interceptor.modifyBeforeRetryLoop(context)
        }

    fun readBeforeAttempt(request: HttpRequest): Result<Unit> {
        // reset http response on attempt to prevent an invalid final context
        _lastHttpResponse = null
        val input = checkNotNull(_lastInput)
        val context = HttpProtocolRequestInterceptorContext(input as Any, request, execContext)
        return executeAll { interceptor ->
            interceptor.readBeforeAttempt(context)
        }
    }

    suspend fun modifyBeforeSigning(request: HttpRequest): HttpRequest =
        modifyHttpRequestHook(request) { interceptor, context ->
            interceptor.modifyBeforeSigning(context)
        }

    fun readBeforeSigning(request: HttpRequest) = readHttpHook(request) { interceptor, context ->
        interceptor.readBeforeSigning(context)
    }

    fun readAfterSigning(request: HttpRequest) = readHttpHook(request) { interceptor, context ->
        interceptor.readAfterSigning(context)
    }

    suspend fun modifyBeforeTransmit(request: HttpRequest): HttpRequest =
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

    suspend fun modifyBeforeDeserialization(call: HttpCall): HttpResponse {
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

    suspend fun modifyBeforeAttemptCompletion(result: Result<O>, httpRequest: HttpRequest, httpResponse: HttpResponse?): Result<O> {
        val input = checkNotNull(_lastInput)

        @Suppress("UNCHECKED_CAST")
        val context = HttpAttemptInterceptorContext(input as Any, result as Result<Any>, httpRequest, httpResponse, execContext)

        val modified = runCatching {
            interceptors.fold(context) { ctx, interceptor ->
                val modified = interceptor.modifyBeforeAttemptCompletion(ctx)
                checkResultType<O>("modifyBeforeAttemptCompletion", modified, typeInfo.outputType)
                ctx.response = modified
                ctx
            }.response
        }

        return modified.fold(
            { checkResultType("modifyBeforeAttemptCompletion", it, typeInfo.outputType) },
            { Result.failure(it) },
        )
    }

    fun readAfterAttempt(result: Result<O>, httpRequest: HttpRequest, httpResponse: HttpResponse?) {
        val input = checkNotNull(_lastInput)
        _lastHttpResponse = httpResponse

        @Suppress("UNCHECKED_CAST")
        val context = HttpAttemptInterceptorContext(input as Any, result as Result<Any>, httpRequest, httpResponse, execContext)
        val readResult = executeAll { interceptor ->
            interceptor.readAfterAttempt(context)
        }

        return readResult.getOrThrow()
    }

    suspend fun modifyBeforeCompletion(result: Result<O>): Result<O> {
        // SAFETY: If we started executing an operation at all the input will be set at least once
        val input = checkNotNull(_lastInput)

        @Suppress("UNCHECKED_CAST")
        val context = HttpFinalInterceptorContext(input as Any, result as Result<Any>, _lastHttpRequest, _lastHttpResponse, execContext)

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

    fun readAfterExecution(result: Result<O>): Result<O> {
        // SAFETY: If we started executing an operation at all input will be set at least once
        val input = checkNotNull(_lastInput)

        @Suppress("UNCHECKED_CAST")
        val context = HttpFinalInterceptorContext(input as Any, result as Result<Any>, _lastHttpRequest, _lastHttpResponse, execContext)
        val readResult = executeAll { interceptor ->
            interceptor.readAfterExecution(context)
        }

        // if an error was encountered it's going to be the result of the operation
        return readResult.fold(
            { checkResultType("readAfterExecution", context.response, typeInfo.outputType) },
            { currEx ->
                // add the original exception as a suppressed exception
                result.exceptionOrNull()?.let { currEx.addSuppressed(it) }
                Result.failure(currEx)
            },
        )
    }
}
