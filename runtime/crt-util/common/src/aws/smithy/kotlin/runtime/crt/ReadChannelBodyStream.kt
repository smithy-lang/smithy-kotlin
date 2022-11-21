/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.crt

import aws.sdk.kotlin.crt.http.HttpRequestBodyStream
import aws.sdk.kotlin.crt.io.MutableBuffer
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.util.InternalApi
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

private val POLLING_DELAY = 100.milliseconds

/**
 * write as much of [outgoing] to [dest] as possible
 */
internal expect fun transferRequestBody(outgoing: SdkBuffer, dest: MutableBuffer)

/**
 * Implement's [HttpRequestBodyStream] which proxies an SDK request body channel [SdkByteReadChannel]
 */
@InternalApi
public class ReadChannelBodyStream(
    // the request body channel
    private val bodyChan: SdkByteReadChannel,
    private val callContext: CoroutineContext,
) : HttpRequestBodyStream, CoroutineScope {

    private val producerJob = Job(callContext.job)
    override val coroutineContext: CoroutineContext = callContext + producerJob

    private val currBuffer = atomic<SdkBuffer?>(null)
    private val bufferChan = Channel<SdkBuffer>(Channel.UNLIMITED)

    private val totalBytesSent = atomic(0L)

    init {
        producerJob.invokeOnCompletion { cause ->
            bodyChan.cancel(cause)
        }

        // Poll the channel's `isClosedForRead` and complete when it's true. This works around a timing issue when the
        // write side of the channel finishes sending bytes but doesn't call `close` in time for the CRT's
        // `sendRequestBody` loop to pick it up. If CRT reads all the bytes it expects, it ceases calling
        // `sendRequestBody`, which risks leaving the producer job open indefinitely. This polling loop catches any
        // missed channel closures and ends the producer job to avoid that issue.
        launch(coroutineContext + CoroutineName("body-channel-watchdog")) {
            while (producerJob.isActive) {
                if (bodyChan.isClosedForRead) {
                    producerJob.complete()
                    return@launch
                }
                delay(POLLING_DELAY)
            }
        }
    }

    // lie - CRT tries to control this via normal seek operations (e.g. when they calculate a hash for signing
    // they consume the aws_input_stream and then seek to the beginning). Instead we either support creating
    // a new read channel or we don't. At this level we don't care, consumers of this type need to understand
    // and handle these concerns.
    override fun resetPosition(): Boolean = true

    override fun sendRequestBody(buffer: MutableBuffer): Boolean =
        doSendRequestBody(buffer).also { if (it) producerJob.complete() }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun doSendRequestBody(buffer: MutableBuffer): Boolean {
        // ensure the request context hasn't been cancelled
        callContext.ensureActive()
        var outgoing = currBuffer.getAndSet(null) ?: bufferChan.tryReceive().getOrNull()

        if (bodyChan.availableForRead > 0 && outgoing == null) {
            // NOTE: It is critical that the coroutine launched doesn't actually suspend because it will never
            // get a chance to resume. The CRT will consume the dispatcher thread until the data has been read
            // completely. We could launch one of the coroutines into a different dispatcher but this won't work
            // on platforms (e.g. JS) that don't have multiple threads. Essentially the CRT will starve
            // the dispatcher and not allow other coroutines to make progress.
            // see: https://github.com/awslabs/aws-sdk-kotlin/issues/282
            //
            // To get around this, if there is data to read we launch a coroutine UNDISPATCHED so that it runs
            // immediately in the current thread. The coroutine will fill the buffer but won't suspend because
            // we know data is available.
            launch(start = CoroutineStart.UNDISPATCHED) {
                val sdkBuffer = SdkBuffer()
                bodyChan.read(sdkBuffer, bodyChan.availableForRead.toLong())
                bufferChan.send(sdkBuffer)
            }.invokeOnCompletion { cause ->
                if (cause != null) {
                    producerJob.completeExceptionally(cause)
                    bufferChan.close(cause)
                }
            }
        }

        if (bodyChan.availableForRead == 0 && bodyChan.isClosedForRead) {
            bufferChan.close()
        }

        if (outgoing == null) {
            if (bufferChan.isClosedForReceive) {
                return true
            }

            outgoing = bufferChan.tryReceive().getOrNull() ?: return false
        }

        val sizeBefore = outgoing.size
        transferRequestBody(outgoing, buffer)

        totalBytesSent += sizeBefore - outgoing.size

        if (outgoing.size > 0L) {
            currBuffer.value = outgoing
        }

        return bufferChan.isClosedForReceive && currBuffer.value == null
    }
}
