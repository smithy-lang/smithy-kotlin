/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.engine.ktor

import io.ktor.client.request.HttpRequestBuilder as KtorRequestBuilder
import io.ktor.client.utils.EmptyContent
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.close
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import software.aws.clientrt.http.HttpBody
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.io.Source

// streaming buffer size. The SDK stream is copied in chunks of this size to the output Ktor ByteReadChannel.
// Too small and we risk too many context switches, too large and we waste memory and (if large enough) become
// CPU cache unfriendly. Disk read/write >> main memory copies so we _shouldn't_ be the bottleneck.
// 4K has historically been chosen for a number of reasons including default disk cluster size as well as default
// OS page size.
//
// NOTE: This says nothing of the underlying outgoing buffer sizes used in Ktor to actually put data on the wire!!
// TODO - we could probably consider allowing this be set from the environment
private const val BUFFER_SIZE = 4096

/**
 * Adapts an SDK HTTP request to something Ktor understands
 */
internal class KtorRequestAdapter(
    private val sdkBuilder: HttpRequestBuilder,
    private val callContext: CoroutineContext
) {

    fun toBuilder(): KtorRequestBuilder {
        // convert the basic request properties (minus the body)
        val builder = sdkBuilder.toKtorRequestBuilder()

        // strip content type header which Ktor doesn't allow set this way for some reason
        val contentHeaders = builder.headers["Content-Type"]
        builder.headers.remove("Content-Type")
        val contentType: ContentType? = contentHeaders?.let { ContentType.parse(it) }

        // convert the request body
        when (val body = sdkBuilder.body) {
            is HttpBody.Empty -> builder.body = EmptyContent
            is HttpBody.Bytes -> builder.body = ByteArrayContent(body.bytes(), contentType)
            is HttpBody.Streaming -> builder.body = proxyRequestStream(body, contentType)
        }

        return builder
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun proxyRequestStream(body: HttpBody.Streaming, contentType: ContentType?): OutgoingContent {
        val source = body.readFrom()
        return object : OutgoingContent.ReadChannelContent() {
            override val contentType: ContentType? = contentType
            override val contentLength: Long? = body.contentLength

            override fun readFrom(): ByteReadChannel {
                // we want to read values off the incoming source and write them to this channel
                val channel = ByteChannel()

                // launch a coroutine to deal with filling the channel, it will be tied to the `callContext`
                val ctx = callContext + Dispatchers.IO + CoroutineName("sdk-to-ktor-stream-proxy")
                GlobalScope.launch(ctx) {
                    try {
                        forwardSource(channel, source)
                        channel.close()
                    } catch (ex: Exception) {
                        // propagate the cause
                        channel.close(ex)
                    }
                }
                return channel
            }

            private suspend fun forwardSource(dst: ByteChannel, source: Source) {
                // TODO - consider a buffer pool here
                val buffer = ByteBuffer.allocate(BUFFER_SIZE)
                while (!source.isClosedForRead) {
                    // fill the buffer by reading chunks from the underlying source
                    while (source.readAvailable(buffer) != -1 && buffer.remaining() > 0) {}
                    buffer.flip()

                    // propagate it to the channel
                    dst.writeFully(buffer)
                    dst.flush()
                    buffer.clear()
                }
            }
        }
    }
}
