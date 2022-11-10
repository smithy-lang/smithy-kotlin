/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.internal.derivedName
import aws.smithy.kotlin.runtime.io.internal.toSdk
import aws.smithy.kotlin.runtime.io.readAll
import kotlinx.coroutines.*
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import kotlin.coroutines.CoroutineContext

/**
 * OkHttp [RequestBody] that reads from [body] channel
 */
internal class ByteChannelRequestBody(
    private val body: HttpBody.Streaming,
    callContext: CoroutineContext,
) : RequestBody(), CoroutineScope {

    private val producerJob = Job(callContext[Job])
    override val coroutineContext: CoroutineContext = callContext + producerJob + callContext.derivedName("send-request-body") + Dispatchers.IO
    override fun contentType(): MediaType? = null
    override fun contentLength(): Long = body.contentLength ?: -1
    override fun isOneShot(): Boolean = !body.isReplayable
    override fun isDuplex(): Boolean = body.isDuplex

    override fun writeTo(sink: BufferedSink) = try {
        doWriteTo(sink)
    } catch (ex: Exception) {
        throw when (ex) {
            is IOException -> ex
            // wrap all exceptions thrown from inside `okhttp3.RequestBody#writeTo(..)` as an IOException
            // see https://github.com/awslabs/aws-sdk-kotlin/issues/733
            else -> IOException(ex)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun doWriteTo(sink: BufferedSink) {
        if (isDuplex()) {
            // launch coroutine that writes to sink in the background
            launch {
                sink.use { transferBody(it) }
            }
        } else {
            // remove the current dispatcher (if it exists) and use the internal
            // runBlocking dispatcher that blocks the *current* thread
            val blockingContext = coroutineContext.minusKey(CoroutineDispatcher)

            // Non-duplex (aka "normal") requests MUST write all of their request body
            // before this function returns. Requests are given a background thread to
            // do this work in, and it is safe and expected to block.
            // see: https://square.github.io/okhttp/4.x/okhttp/okhttp3/-request-body/is-duplex/
            runBlocking(blockingContext) {
                transferBody(sink)
            }
        }
    }
    private suspend fun transferBody(sink: BufferedSink) = withJob(producerJob) {
        val chan = body.readFrom()
        val sdkSink = sink.toSdk()
        chan.readAll(sdkSink)
    }
}

/**
 * Completes the given job when the block returns calling either `complete()` when the block runs
 * successfully or `completeExceptionally()` on exception.
 * @return the result of calling [block]
 */
private inline fun <T> withJob(job: CompletableJob, block: () -> T): T {
    try {
        return block()
    } catch (ex: Exception) {
        job.completeExceptionally(ex)
        throw ex
    } finally {
        job.complete()
    }
}
