/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http.engine.ktor

import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import io.ktor.client.utils.EmptyContent
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext
import io.ktor.client.request.HttpRequestBuilder as KtorRequestBuilder

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
@OptIn(InternalAPI::class)
internal class KtorRequestAdapter(
    private val sdkRequest: HttpRequest,
    callContext: CoroutineContext
) : CoroutineScope {

    internal constructor(builder: HttpRequestBuilder, callContext: CoroutineContext) : this(builder.build(), callContext)

    private val fillRequestJob = Job()
    override val coroutineContext: CoroutineContext = callContext + Dispatchers.IO + fillRequestJob + CoroutineName("sdk-to-ktor-stream-proxy")
    init {
        callContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) {
                fillRequestJob.completeExceptionally(cause)
            } else {
                fillRequestJob.complete()
            }
        }
    }

    fun toBuilder(): KtorRequestBuilder {
        // convert the basic request properties (minus the body)
        val builder = sdkRequest.toKtorRequestBuilder()

        // strip content type header which Ktor doesn't allow set this way for some reason
        val contentHeaders = builder.headers["Content-Type"]
        builder.headers.remove("Content-Type")
        val contentType: ContentType? = contentHeaders?.let { ContentType.parse(it) }

        // convert the request body
        when (val body = sdkRequest.body) {
            is HttpBody.Empty -> builder.body = EmptyContent
            is HttpBody.Bytes -> builder.body = ByteArrayContent(body.bytes(), contentType)
            is HttpBody.Streaming -> builder.body = proxyRequestStream(body, contentType)
        }

        return builder
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun proxyRequestStream(body: HttpBody.Streaming, contentType: ContentType?): OutgoingContent {
        val source = body.readFrom()
        fillRequestJob.invokeOnCompletion { cause ->
            source.cancel(cause)
        }

        return object : OutgoingContent.ReadChannelContent() {
            override val contentType: ContentType? = contentType
            override val contentLength: Long? = body.contentLength
            // FIXME - ensure the `source` is closed?

            override fun readFrom(): ByteReadChannel {
                // FIXME - instead of reading and writing bytes we could probably proxy the underlying channel
                // and/or since we use ktor under the hood if we could access the underlying channel that would be best
                // we want to read values off the incoming source and write them to this channel
                val channel = ByteChannel()

                // launch a coroutine to deal with filling the channel
                val proxyJob = launch {
                    try {
                        forwardSource(channel, source)
                        channel.close()
                    } catch (ex: Exception) {
                        // propagate the cause
                        channel.close(ex)
                    }
                }

                proxyJob.invokeOnCompletion { cause ->
                    if (cause != null) {
                        fillRequestJob.completeExceptionally(cause)
                    } else {
                        fillRequestJob.complete()
                    }
                }

                return channel
            }

            private suspend fun forwardSource(dst: ByteChannel, source: SdkByteReadChannel) {
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
