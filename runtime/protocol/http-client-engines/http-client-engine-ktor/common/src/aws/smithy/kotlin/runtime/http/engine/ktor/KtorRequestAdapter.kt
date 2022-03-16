/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http.engine.ktor

import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import io.ktor.client.utils.EmptyContent
import io.ktor.http.*
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import io.ktor.client.request.HttpRequestBuilder as KtorRequestBuilder

/**
 * Adapts an SDK HTTP request to something Ktor understands
 */
internal class KtorRequestAdapter(
    private val sdkRequest: HttpRequest,
    callContext: CoroutineContext
) : CoroutineScope {

    internal constructor(builder: HttpRequestBuilder, callContext: CoroutineContext) : this(builder.build(), callContext)

    private val fillRequestJob = Job()
    override val coroutineContext: CoroutineContext = callContext + ioDispatcher() + fillRequestJob + CoroutineName("sdk-to-ktor-stream-proxy")
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

        return object : OutgoingContent.ReadChannelContent() {
            override val contentType: ContentType? = contentType
            override val contentLength: Long? = body.contentLength
            override fun readFrom(): ByteReadChannel = channel
        }
    }
}
