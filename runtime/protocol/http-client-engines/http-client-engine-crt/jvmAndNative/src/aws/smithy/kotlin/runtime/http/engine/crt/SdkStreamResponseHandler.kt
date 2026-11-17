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
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Implements the CRT stream response interface which proxies the response from the CRT to the SDK
 * @param conn The HTTP connection used to make the request. Will be closed when the response handler completes
 */
@OptIn(DelicateCoroutinesApi::class)
internal class SdkStreamResponseHandler(
    private val conn: HttpClientConnection,
    private val callContext: CoroutineContext,
    private val windowSizeBytes: Int,
) : HttpStreamResponseHandler {
    // TODO - need to cancel the stream when the body is closed from the caller side early.
    // There is no great way to do that currently without either (1) closing the connection or (2) throwing an
    // exception from a callback such that AWS_OP_ERROR is returned. Wait for HttpStream to have explicit cancellation

    init {
        // Channel buffer and CRT window are both sized from windowSizeBytes; fail fast on a bad value here rather
        // than as a mid-stream body failure.
        require(windowSizeBytes > 0) { "windowSizeBytes must be > 0, was $windowSizeBytes" }
    }

    private val logger = callContext.logger<SdkStreamResponseHandler>()
    private val responseReady = Channel<HttpResponse>(1)
    private val headers = HeadersBuilder()

    // Body bytes are written directly into this channel from onResponseBody (a non-suspending CRT callback).
    // The channel buffer is sized to the CRT flow-control window, so no more than [windowSizeBytes] un-acked bytes
    // are ever in-flight and the synchronous write always has room.
    private val bodyChan = SdkByteChannel(true, windowSizeBytes)

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
    private var receivedBodyData = false

    // set true once a consumable body channel has been handed to the caller . When the response was signalled
    // with no body (HttpBody.Empty) nothing ever drains bodyChan, so any body bytes that still arrive must be
    // discarded rather than written — otherwise the bounded buffer fills and the stream stalls.
    private var bodyConsumable = false

    /**
     * Called by the response read channel as data is consumed
     * @param size the number of bytes consumed
     */
    private fun onDataConsumed(size: Int) {
        lock.withLock {
            crtStream?.incrementWindow(size)
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
        // Reads from [bodyChan] drive CRT window replenishment: as the downstream consumer drains bytes we hand the
        // same number of bytes back to the flow-control window.
        val ch = WindowManagedReadChannel(bodyChan, ::onDataConsumed)
        bodyConsumable = true
        return object : HttpBody.ChannelContent() {
            override val contentLength: Long? = contentLength
            override fun readFrom(): SdkByteReadChannel = ch
        }
    }

    // signal response ready and engine can proceed (all that is required is headers, body is consumed asynchronously)
    private fun signalResponse(stream: HttpStream) {
        // already signalled
        if (responseReady.isClosedForSend) return

        val transferEncoding = headers["Transfer-Encoding"]?.lowercase()
        val chunked = transferEncoding == "chunked"
        val contentLength = headers["Content-Length"]?.toLong()
        val status = HttpStatusCode.fromValue(stream.responseStatusCode)
        val isEventStream = headers["Content-Type"]?.contains("application/vnd.amazon.eventstream") == true

        val hasBody = if ((chunked || isEventStream) && contentLength == null) {
            // Some responses are chunked or event streams but have an empty body.
            // If we get a response with an unknown content length,
            // ensure we actually received a body when setting hasBody
            receivedBodyData
        } else {
            (contentLength != null && contentLength > 0) &&
                (status !in listOf(HttpStatusCode.NotModified, HttpStatusCode.NoContent)) &&
                !status.isInformational()
        }

        val body = when (hasBody) {
            false -> HttpBody.Empty
            true -> createHttpResponseBody(contentLength)
        }

        val resp = HttpResponse(
            status,
            headers.build(),
            body,
        )

        val result = responseReady.trySend(resp)
        check(result.isSuccess) { "signalling response failed, result was: ${result.exceptionOrNull()}" }
        responseReady.close()
    }

    override fun onResponseHeadersDone(stream: HttpStream, blockType: Int) {
        if (!blockType.isMainHeadersBlock) return
        val chunked = headers["Transfer-Encoding"]?.lowercase() == "chunked"
        val contentLength = headers["Content-Length"]?.toLong()
        val isEventStream = headers["Content-Type"]?.contains("application/vnd.amazon.eventstream") == true
        if ((chunked || isEventStream) && contentLength == null) return // Defer signaling until we know there's body data
        signalResponse(stream)
    }

    /**
     * Abort the response stream and stop CRT delivering more body data. Optionally fails the body channel with
     * [cause] so a downstream reader observes it. Returns [discardedLen] so the caller can report the incoming bytes
     * as consumed to CRT (the sliding window advances but the bytes are dropped).
     */
    private fun abortStream(stream: HttpStream, discardedLen: Int, cause: Throwable? = null): Int {
        if (cause != null) bodyChan.close(cause)
        lock.withLock {
            crtStream?.close()
            crtStream = null
        }
        stream.close()
        return discardedLen
    }

    override fun onResponseBody(stream: HttpStream, bodyBytesIn: Buffer): Int {
        val isCancelled = lock.withLock {
            crtStream = stream
            cancelled
        }

        // short circuit, stop buffering data and discard remaining incoming bytes. This also covers the case where
        // the downstream consumer closed/cancelled the body channel early.
        if (isCancelled || bodyChan.isClosedForWrite) {
            return abortStream(stream, bodyBytesIn.len)
        }

        if (!receivedBodyData) {
            receivedBodyData = true
            signalResponse(stream)
        }

        // The response was signalled with no consumable body (e.g. Content-Length: 0, 204/304, or an informational
        // status) yet the server sent body bytes anyway. Nothing will ever drain bodyChan, so writing here would fill
        // the bounded buffer and stall the stream. Discard the bytes and report them consumed so the window advances.
        if (!bodyConsumable) {
            return bodyBytesIn.len
        }

        val buffer = SdkBuffer().apply {
            val bytes = bodyBytesIn.readAll()
            write(bytes)
        }

        // Write directly into the channel buffer without suspending.
        val wc = buffer.size
        val written = try {
            bodyChan.tryWrite(buffer, wc)
        } catch (e: Throwable) {
            // The consumer may have closed the channel between the isClosedForWrite check above and here, in which
            // case tryWrite throws the (arbitrary) close cause. If now closed, discard the bytes rather than letting
            // it escape the CRT native callback; otherwise it's a genuine bug, so rethrow.
            if (bodyChan.isClosedForWrite) {
                return abortStream(stream, bodyBytesIn.len)
            }
            throw e
        }

        // INVARIANT: written == wc (buffer sized to the CRT window, credit returned only after space frees). If ever
        // violated, fail deterministically instead of silently dropping bytes or throwing out of the native callback.
        if (written != wc) {
            val cause = IllegalStateException(
                "response body channel accepted only $written of $wc bytes; the channel buffer " +
                    "($windowSizeBytes) is smaller than the CRT flow-control window and body bytes would be lost",
            )
            logger.error(cause) { "flow-control invariant violated; failing response stream" }
            return abortStream(stream, bodyBytesIn.len, cause)
        }

        // explicit window management is handled by `onDataConsumed` as data is read from the channel
        return 0
    }

    override fun onResponseComplete(stream: HttpStream, errorCode: Int) {
        // close the body channel and signal response BEFORE closing the stream,
        // since signalResponse needs to read stream.responseStatusCode
        if (errorCode != 0) {
            val ex = crtException(errorCode)
            responseReady.close(ex)
            bodyChan.close(ex)
        } else {
            // closing the channel to indicate no more data will be sent
            bodyChan.close()
            // ensure a response was signalled (will close the channel on it's own if it wasn't already sent)
            signalResponse(stream)
        }

        // stream is only valid until the end of this callback, ensure any further data being read downstream
        // doesn't call incrementWindow on a resource that has been freed
        lock.withLock {
            crtStream?.close()
            crtStream = null
            streamCompleted = true
        }
        stream.close()
    }

    internal suspend fun waitForResponse(): HttpResponse = responseReady.receive()

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
                // wake any consumer suspended reading the body; onResponseComplete may never fire once we've
                // force-shutdown the connection. close is idempotent so this is a no-op if already closed.
                bodyChan.close(CancellationException("response stream did not complete before the call was finished"))
            }

            logger.trace { "Closing connection ${conn.id}" }
            // return to pool
            conn.close()
        }
    }
}

/**
 * Read-side decorator over [delegate] that returns CRT flow-control window credit as bytes are consumed.
 * Every successful [read] hands the number of bytes read back to [onConsumed], which increments the CRT stream
 * window. This ties backpressure to actual downstream consumption: CRT will not deliver more data until the
 * consumer has drained (and thereby acknowledged) previously delivered bytes.
 */
private class WindowManagedReadChannel(
    private val delegate: SdkByteChannel,
    private val onConsumed: (Int) -> Unit,
) : SdkByteReadChannel by delegate {
    override suspend fun read(sink: SdkBuffer, limit: Long): Long {
        // ORDERING: free buffer space (delegate.read) before returning window credit (onConsumed). Crediting first
        // would let CRT deliver into a still-full buffer, breaking the "channel capacity == window" guarantee.
        val rc = delegate.read(sink, limit)
        if (rc > 0) onConsumed(rc.toInt())
        return rc
    }
}
