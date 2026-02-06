/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.crt

import aws.sdk.kotlin.crt.http.*
import aws.sdk.kotlin.crt.io.Buffer
import aws.smithy.kotlin.runtime.http.HeadersBuilder
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.isInformational
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkByteChannel
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.telemetry.logging.logger
import aws.smithy.kotlin.runtime.util.derivedName
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Implements the CRT stream response interface which proxies the response from the CRT to the SDK
 * @param conn The HTTP connection used to make the request. Will be closed when the response handler completes
 */
@OptIn(DelicateCoroutinesApi::class)
internal class SdkStreamResponseHandler(
    private val conn: HttpClientConnection,
    private val callContext: CoroutineContext,
) : HttpStreamResponseHandler {
    // TODO - need to cancel the stream when the body is closed from the caller side early.
    // There is no great way to do that currently without either (1) closing the connection or (2) throwing an
    // exception from a callback such that AWS_OP_ERROR is returned. Wait for HttpStream to have explicit cancellation

    private val logger = callContext.logger<SdkStreamResponseHandler>()
    private val responseReady = Channel<HttpResponse>(1)
    private val headers = HeadersBuilder()

    // in practice only WINDOW_SIZE bytes will ever be in-flight
    private val bodyChan = Channel<SdkBuffer>(Channel.UNLIMITED)

    private val lock = reentrantLock() // protects crtStream and cancelled state
    private var crtStream: HttpStream? = null

    // if the (coroutine) job is completed before the stream's onResponseComplete callback is
    // invoked (for any reason) we consider the stream "cancelled"
    private var cancelled = false

    private val Int.isMainHeadersBlock: Boolean
        get() = when (this) {
            HttpHeaderBlock.MAIN.blockType -> true
            else -> false
        }

    private var streamCompleted = false

    /**
     * Called by the response read channel as data is consumed
     * @param size the number of bytes consumed
     */
    private fun onDataConsumed(size: Int) {
        lock.withLock {
            println("SdkStreamResponseHandler -> onDataConsumed: size=$size, crtStream=$crtStream, streamCompleted=$streamCompleted")
            if (streamCompleted) {
                println("SdkStreamResponseHandler -> onDataConsumed: stream already completed, skipping incrementWindow")
                return
            }
            crtStream?.incrementWindow(size)
            println("SdkStreamResponseHandler -> onDataConsumed: incrementWindow completed")
        }
    }

    override fun onResponseHeaders(
        stream: HttpStream,
        responseStatusCode: Int,
        blockType: Int,
        nextHeaders: List<HttpHeader>?,
    ) {
        if (!blockType.isMainHeadersBlock) return

        nextHeaders?.forEach {
            headers.append(it.name, it.value)
        }
    }

    private fun createHttpResponseBody(contentLength: Long?): HttpBody {
        println("SdkStreamResponseHandler -> createHttpResponseBody: contentLength=$contentLength")
        val ch = SdkByteChannel(true)
        val writerContext = callContext + callContext.derivedName("response-body-writer")
        val job = GlobalScope.launch(writerContext) {
            println("SdkStreamResponseHandler -> body writer coroutine: started")
            val result = runCatching {
                for (buffer in bodyChan) {
                    println("SdkStreamResponseHandler -> body writer: received buffer size=${buffer.size}")
                    val wc = buffer.size.toInt()
                    ch.write(buffer)
                    println("SdkStreamResponseHandler -> body writer: wrote to channel, calling onDataConsumed")
                    // increment window
                    onDataConsumed(wc)
                    println("SdkStreamResponseHandler -> body writer: onDataConsumed returned")
                }
            }

            println("SdkStreamResponseHandler -> body writer: loop completed, result=${result.exceptionOrNull()}")
            // immediately close when done to signal end of body stream
            println("SdkStreamResponseHandler -> body writer: calling ch.close")
            ch.close(result.exceptionOrNull())
            println("SdkStreamResponseHandler -> body writer: ch.close returned")
        }

        job.invokeOnCompletion { cause ->
            println("SdkStreamResponseHandler -> body writer job: invokeOnCompletion called with cause=$cause")
            // close is idempotent, if not previously closed then close with cause
            println("SdkStreamResponseHandler -> body writer job: calling ch.close in completion handler")
            ch.close(cause)
            println("SdkStreamResponseHandler -> body writer job: ch.close in completion handler returned")
        }
        return object : HttpBody.ChannelContent() {
            override val contentLength: Long? = contentLength
            override fun readFrom(): SdkByteReadChannel = ch
        }
    }

    // signal response ready and engine can proceed (all that is required is headers, body is consumed asynchronously)
    private fun signalResponse(stream: HttpStream) {
        println("SdkStreamResponseHandler -> signalResponse: called")
        // already signalled
        if (responseReady.isClosedForSend) {
            println("SdkStreamResponseHandler -> signalResponse: already signalled, returning")
            return
        }

        val transferEncoding = headers["Transfer-Encoding"]?.lowercase()
        val chunked = transferEncoding == "chunked"
        val contentLength = headers["Content-Length"]?.toLong()
        val status = HttpStatusCode.fromValue(stream.responseStatusCode)

        val hasBody = ((contentLength != null && contentLength > 0) || chunked) &&
            (status !in listOf(HttpStatusCode.NotModified, HttpStatusCode.NoContent)) &&
            !status.isInformational()

        println("SdkStreamResponseHandler -> signalResponse: status=$status, hasBody=$hasBody, contentLength=$contentLength")

        val body = when (hasBody) {
            false -> HttpBody.Empty
            true -> createHttpResponseBody(contentLength)
        }

        println("SdkStreamResponseHandler -> signalResponse: creating HttpResponse")
        val resp = HttpResponse(
            status,
            headers.build(),
            body,
        )

        println("SdkStreamResponseHandler -> signalResponse: sending response to channel")
        val result = responseReady.trySend(resp)
        check(result.isSuccess) { "signalling response failed, result was: ${result.exceptionOrNull()}" }
        responseReady.close()
        println("SdkStreamResponseHandler -> signalResponse: completed")
    }

    override fun onResponseHeadersDone(stream: HttpStream, blockType: Int) {
        if (!blockType.isMainHeadersBlock) return
        signalResponse(stream)
    }

    override fun onResponseBody(stream: HttpStream, bodyBytesIn: Buffer): Int {
        println("SdkStreamResponseHandler -> onResponseBody: received ${bodyBytesIn.len} bytes")
        val isCancelled = lock.withLock {
            crtStream = stream
            cancelled
        }

        // short circuit, stop buffering data and discard remaining incoming bytes
        if (isCancelled) {
            println("SdkStreamResponseHandler -> onResponseBody: cancelled, discarding data")
            return bodyBytesIn.len
        }

        val buffer = SdkBuffer().apply {
            val bytes = bodyBytesIn.readAll()
            write(bytes)
        }

        println("SdkStreamResponseHandler -> onResponseBody: sending buffer to bodyChan")
        bodyChan.trySend(buffer).getOrThrow()
        println("SdkStreamResponseHandler -> onResponseBody: buffer sent, returning 0")

        // explicit window management is handled by `onDataConsumed` as data is read from the channel
        return 0
    }

    override fun onResponseComplete(stream: HttpStream, errorCode: Int) {
        println("SdkStreamResponseHandler -> onResponseComplete: errorCode=$errorCode")
        // stream is only valid until the end of this callback, ensure any further data being read downstream
        // doesn't call incrementWindow on a resource that has been free'd
        lock.withLock {
            println("SdkStreamResponseHandler -> onResponseComplete: nulling out crtStream reference")
            crtStream = null
            streamCompleted = true
        }
        println("SdkStreamResponseHandler -> onResponseComplete: NOT closing stream (CRT will handle it)")

        // close the body channel
        if (errorCode != 0) {
            println("SdkStreamResponseHandler -> onResponseComplete: error, closing channels with exception")
            val ex = crtException(errorCode)
            responseReady.close(ex)
            bodyChan.close(ex)
        } else {
            println("SdkStreamResponseHandler -> onResponseComplete: success, closing bodyChan")
            // closing the channel to indicate no more data will be sent
            bodyChan.close()
            // ensure a response was signalled (will close the channel on it's own if it wasn't already sent)
            println("SdkStreamResponseHandler -> onResponseComplete: signalling response")
            signalResponse(stream)
        }
        println("SdkStreamResponseHandler -> onResponseComplete: completed")
    }

    internal suspend fun waitForResponse(): HttpResponse {
        println("SdkStreamResponseHandler -> waitForResponse: waiting for response")
        val resp = responseReady.receive()
        println("SdkStreamResponseHandler -> waitForResponse: received response, status=${resp.status}")
        return resp
    }

    /**
     * Invoked only after the consumer is finished with the response and it is safe to cleanup resources
     */
    internal fun complete() {
        // We have no way of cancelling the stream, we have to drive it to exhaustion OR close the connection.
        // At this point we know it's safe to release resources so if the stream hasn't completed yet
        // we forcefully shutdown the connection. This can happen when the stream's window is full and it's waiting
        // on the window to be incremented to proceed (i.e. the user didn't consume the stream for whatever reason
        // and more data is pending arrival). It can also happen if the coroutine for this request is cancelled
        // before onResponseComplete fires.
        lock.withLock {
            val forceClose = !streamCompleted

            if (forceClose) {
                logger.debug { "stream did not complete before job, forcing connection shutdown! handler=$this; conn=$conn; conn.id=${conn.id}; stream=$crtStream" }
                conn.shutdown()
                cancelled = true
            }

            logger.trace { "Closing connection ${conn.id}" }
            // return to pool
            conn.close()
        }
    }
}
