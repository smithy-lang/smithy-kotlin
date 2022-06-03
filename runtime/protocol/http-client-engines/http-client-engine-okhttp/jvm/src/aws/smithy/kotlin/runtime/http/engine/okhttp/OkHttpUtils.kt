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
import okhttp3.internal.http.HttpMethod
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

    val engineBody = if (HttpMethod.permitsRequestBody(method.name)) {
        when (val body = body) {
            is HttpBody.Empty -> ByteArray(0).toRequestBody(null, 0, 0)
            is HttpBody.Bytes -> body.bytes().let { it.toRequestBody(null, 0, it.size) }
            is HttpBody.Streaming -> ByteChannelRequestBody(body, callContext)
        }
    } else {
        // assert we don't silently ignore a body even though one is unexpected here
        check(body is HttpBody.Empty) { "unexpected HTTP body for method $method" }
        null
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
    val httpBody = if (body.contentLength() != 0L) {
        val ch = SdkByteChannel(true)
        val writerContext = callContext + Dispatchers.IO + callContext.derivedName("response-body-writer")
        val job = GlobalScope.launch(writerContext) {
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
            // -1 is used by okhttp as transfer-encoding chunked
            override val contentLength: Long? = if (body.contentLength() >= 0L) body.contentLength() else null
            override fun readFrom(): SdkByteReadChannel = ch
        }
    } else {
        HttpBody.Empty
    }

    return HttpResponse(HttpStatusCode.fromValue(code), sdkHeaders, httpBody)
}

/**
 * Append to the existing coroutine name if it exists in the context otherwise
 * use [name] as is.
 * @return the [CoroutineName] context element
 */
private fun CoroutineContext.derivedName(name: String): CoroutineName {
    val existing = get(CoroutineName)?.name ?: return CoroutineName(name)
    return CoroutineName("$existing:$name")
}
