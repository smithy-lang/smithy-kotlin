/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.engine

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import software.aws.clientrt.ClientException
import software.aws.clientrt.http.request.HttpRequest
import software.aws.clientrt.http.response.HttpCall
import software.aws.clientrt.io.Closeable
import software.aws.clientrt.util.InternalApi
import kotlin.coroutines.CoroutineContext

/**
 * Functionality a real HTTP client must provide
 */
interface HttpClientEngine : Closeable, CoroutineScope {
    /**
     * Execute a single HTTP request and return the response.
     * Consumers *MUST* call `HttpCall.complete()` when finished processing the response
     */
    suspend fun roundTrip(request: HttpRequest): HttpCall

    /**
     * Shutdown and cleanup any resources
     */
    override fun close() { /* pass */ }
}

/**
 * Base class that all concrete [HttpClientEngine] should inherit from
 */
@InternalApi
public abstract class HttpClientEngineBase(engineName: String) : HttpClientEngine {
    // why SupervisorJob? because failure of individual requests should not affect other requests or the overall engine
    override val coroutineContext: CoroutineContext = SupervisorJob() + CoroutineName("http-client-engine-$engineName-context")
    private val closed = atomic(false)

    override fun close() {
        // ensure engine close is idempotent independent of SdkHttpClient
        if (!closed.compareAndSet(false, true)) return

        val job = coroutineContext[Job] as? CompletableJob ?: return

        job.complete()
        job.invokeOnCompletion {
            shutdown()
        }
    }

    /**
     * Shutdown the engine completely and release resources. When this is invoked the engine
     * is guaranteed no new requests will come and that all in-flight requests have completed
     */
    protected open fun shutdown() { }
}

/**
 * Indicates an [HttpClientEngine] is closed already and no further requests should be initiated.
 */
class HttpClientEngineClosedException(override val cause: Throwable? = null) : ClientException("HttpClientEngine already closed")
