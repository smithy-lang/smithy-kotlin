/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.http.HttpBody
import kotlinx.coroutines.*
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.nio.ByteBuffer
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

    @OptIn(ExperimentalStdlibApi::class)
    override fun writeTo(sink: BufferedSink) {
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
        val buffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE)
        while (!chan.isClosedForRead && producerJob.isActive) {
            // fill the buffer by reading chunks from the underlying source
            while (chan.readAvailable(buffer) != -1 && buffer.remaining() > 0) {}

            buffer.flip()
            while (buffer.remaining() > 0) {
                sink.write(buffer)
            }

            buffer.clear()
        }
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
