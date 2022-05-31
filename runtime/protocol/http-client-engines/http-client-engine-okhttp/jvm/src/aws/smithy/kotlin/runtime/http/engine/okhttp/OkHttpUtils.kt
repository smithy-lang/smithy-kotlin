/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.io.SdkByteChannel
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import kotlinx.coroutines.*
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.CoroutineContext
import okhttp3.Request as OkHttpRequest
import okhttp3.Response as OkHttpResponse

/**
 * SDK specific "tag" attached to an [okhttp3.Request] instance
 */
internal data class SdkRequestTag(val execContext: ExecutionContext)

// matches segment size used by okio
// see https://github.com/square/okio/blob/parent-3.1.0/okio/src/commonMain/kotlin/okio/Segment.kt#L179
internal const val DEFAULT_BUFFER_SIZE: Int = 8192

// TODO - do we need this
// private val emptyRequestBody = ByteArray(0).toRequestBody(null, 0, 0)

/**
 * Convert SDK [HttpRequest] to an [okhttp3.Request] instance
 */
internal fun HttpRequest.toOkHttpRequest(
    execContext: ExecutionContext,
    callContext: CoroutineContext
): OkHttpRequest {
    val builder = OkHttpRequest.Builder()
    builder.tag(SdkRequestTag::class, SdkRequestTag(execContext))

    builder.url(url.toString())

    headers.forEach { key, values ->
        values.forEach {
            builder.addHeader(key, it)
        }
    }

    val engineBody = when (val body = body) {
        is HttpBody.Empty -> null
        is HttpBody.Bytes -> body.bytes().let { it.toRequestBody(null, 0, it.size) }
        is HttpBody.Streaming -> ByteChannelRequestBody(body, callContext)
    }

    builder.method(method.name, engineBody)

    return builder.build()
}

/**
 * Convert an [okhttp3.Response] to an SDK [HttpResponse]
 */
@OptIn(DelicateCoroutinesApi::class)
internal fun OkHttpResponse.toSdkResponse(callContext: CoroutineContext): HttpResponse {
    val sdkHeaders = OkHttpHeadersAdapter(headers)
    val httpBody = if (body.contentLength() > 0L) {
        val ch = SdkByteChannel(true)
        val job = GlobalScope.launch(callContext + Dispatchers.IO) {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            val source = body.source()

            while (!source.exhausted()) {
                val rc = source.read(buffer)
                if (rc == -1) break
                ch.writeFully(buffer, 0, rc)
            }
        }

        job.invokeOnCompletion {
            ch.close(it)
        }

        object : HttpBody.Streaming() {
            override val contentLength: Long = body.contentLength()
            override fun readFrom(): SdkByteReadChannel = ch
        }
    } else {
        HttpBody.Empty
    }

    return HttpResponse(HttpStatusCode.fromValue(code), sdkHeaders, httpBody)
}
