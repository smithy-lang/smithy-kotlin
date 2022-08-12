/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.http.HttpBody
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
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
    private val callContext: CoroutineContext,
) : RequestBody() {
    override fun contentType(): MediaType? = null
    override fun contentLength(): Long = body.contentLength ?: -1
    override fun isOneShot(): Boolean = !body.isReplayable

    // TODO - enable for event streams. Requires different processing of request body
    override fun isDuplex(): Boolean = false

    @OptIn(ExperimentalStdlibApi::class)
    override fun writeTo(sink: BufferedSink) {
        // remove the current dispatcher (if it exists) and use the internal
        // runBlocking dispatcher that blocks the current thread
        val sendContext = callContext.minusKey(CoroutineDispatcher) + callContext.derivedName("send-request-body")

        // Non-duplex (aka "normal") requests MUST write all of their request body
        // before this function returns. Requests are given a background thread to
        // do this work in, and it is safe and expected to block.
        // see: https://square.github.io/okhttp/4.x/okhttp/okhttp3/-request-body/is-duplex/
        runBlocking(sendContext) {
            val chan = body.readFrom()
            val buffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE)
            while (!chan.isClosedForRead) {
                // fill the buffer by reading chunks from the underlying source
                while (chan.readAvailable(buffer) != -1 && buffer.remaining() > 0) {
                }

                buffer.flip()
                while (buffer.remaining() > 0) {
                    sink.write(buffer)
                }

                buffer.clear()
            }
        }
    }
}
