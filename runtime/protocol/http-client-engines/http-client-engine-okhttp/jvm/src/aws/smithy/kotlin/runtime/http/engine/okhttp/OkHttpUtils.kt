/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.engine.ProxyConfig
import aws.smithy.kotlin.runtime.http.engine.internal.HttpClientMetrics
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.io.internal.toSdk
import aws.smithy.kotlin.runtime.net.*
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.Authenticator
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.http.HttpMethod
import java.io.IOException
import java.net.*
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.CoroutineContext
import aws.smithy.kotlin.runtime.http.engine.ProxySelector as SdkProxySelector
import okhttp3.Request as OkHttpRequest
import okhttp3.Response as OkHttpResponse

/**
 * SDK specific "tag" attached to an [okhttp3.Request] instance
 */
internal data class SdkRequestTag(val execContext: ExecutionContext, val callContext: CoroutineContext, val metrics: HttpClientMetrics)

/**
 * Convert SDK [HttpRequest] to an [okhttp3.Request] instance
 */
internal fun HttpRequest.toOkHttpRequest(
    execContext: ExecutionContext,
    callContext: CoroutineContext,
    metrics: HttpClientMetrics,
): OkHttpRequest {
    val builder = OkHttpRequest.Builder()
    builder.tag(SdkRequestTag::class, SdkRequestTag(execContext, callContext, metrics))

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
            is HttpBody.SourceContent, is HttpBody.ChannelContent -> {
                val updatedBody: HttpBody = headers["Content-Length"]?.let {
                    if (body.contentLength == null || body.contentLength == -1L) {
                        when (body) {
                            is HttpBody.SourceContent -> body.readFrom().toHttpBody(it.toLong())
                            is HttpBody.ChannelContent -> body.readFrom().toHttpBody(it.toLong())
                            else -> null
                        }
                    } else { null }
                } ?: body
                StreamingRequestBody(updatedBody, callContext)
            }
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
internal fun OkHttpResponse.toSdkResponse(): HttpResponse {
    val sdkHeaders = OkHttpHeadersAdapter(headers)
    val httpBody = if (body.contentLength() != 0L) {
        object : HttpBody.SourceContent() {
            override val isOneShot: Boolean = true

            // -1 is used by okhttp as transfer-encoding chunked
            override val contentLength: Long? = if (body.contentLength() >= 0L) body.contentLength() else null
            override fun readFrom(): SdkSource = body.source().toSdk()
        }
    } else {
        HttpBody.Empty
    }

    return HttpResponse(HttpStatusCode.fromValue(code), sdkHeaders, httpBody)
}

internal class OkHttpProxyAuthenticator(
    private val selector: SdkProxySelector,
) : Authenticator {
    override fun authenticate(route: Route?, response: okhttp3.Response): okhttp3.Request? {
        if (response.request.header("Proxy-Authorization") != null) {
            // Give up, we've already failed to authenticate.
            return null
        }

        val url = response.request.url.let {
            Url(scheme = Scheme(it.scheme, it.port), host = Host.parse(it.host), port = it.port)
        }

        // NOTE: We will end up querying the proxy selector twice. We do this to allow
        // the url.userInfo be used for Basic auth scheme. Supporting other auth schemes
        // will require defining dedicated proxy auth configuration APIs that work
        // on a per/request basis (much like the okhttp interface we are implementing here...)
        val userInfo = when (val proxyConfig = selector.select(url)) {
            is ProxyConfig.Http -> proxyConfig.url.userInfo
            else -> null
        } ?: return null

        for (challenge in response.challenges()) {
            if (challenge.scheme.lowercase() == "okhttp-preemptive" || challenge.scheme == "Basic") {
                return response.request.newBuilder()
                    .header("Proxy-Authorization", Credentials.basic(userInfo.username, userInfo.password))
                    .build()
            }
        }

        return null
    }
}

internal class OkHttpDns(
    private val hr: HostResolver,
) : Dns {
    // we assume OkHttp is calling us on an IO thread already
    override fun lookup(hostname: String): List<InetAddress> = runBlocking {
        val results = hr.resolve(hostname)
        results.map { it.toInetAddress() }
    }
}

internal class OkHttpProxySelector(
    private val sdkSelector: SdkProxySelector,
) : ProxySelector() {
    override fun select(uri: URI?): List<Proxy> {
        if (uri == null) return emptyList()
        val url = uri.toUrl()

        return when (val proxyConfig = sdkSelector.select(url)) {
            is ProxyConfig.Http -> {
                val okProxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyConfig.url.host.toString(), proxyConfig.url.port))
                return listOf(okProxy)
            }
            else -> emptyList()
        }
    }
    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
}

private fun URI.toUrl(): Url {
    val uri = this
    return UrlBuilder {
        scheme = Scheme.parse(uri.scheme)

        // OkHttp documentation calls out that v6 addresses will contain the []s
        host = Host.parse(if (uri.host.startsWith("[")) uri.host.substring(1 until uri.host.length - 1) else uri.host)

        port = uri.port.takeIf { it > 0 }
        path = uri.path

        if (uri.query != null && uri.query.isNotBlank()) {
            val parsedParameters = uri.query.splitAsQueryParameters()
            parameters.appendAll(parsedParameters)
        }

        userInfo = uri.userInfo?.takeIf { it.isNotBlank() }
            ?.let(::UserInfo)

        fragment = uri.fragment?.takeIf { it.isNotBlank() }
    }
}

internal inline fun<T> mapOkHttpExceptions(block: () -> T): T =
    try {
        block()
    } catch (ex: IOException) {
        throw HttpException(ex, ex.errCode(), ex.isRetryable())
    }

private fun Exception.isRetryable(): Boolean = isCauseOrSuppressed<ConnectException>()
private fun Exception.errCode(): HttpErrorCode = when {
    isConnectTimeoutException() -> HttpErrorCode.CONNECT_TIMEOUT
    isCauseOrSuppressed<SocketTimeoutException>() -> HttpErrorCode.SOCKET_TIMEOUT
    isCauseOrSuppressed<SSLHandshakeException>() -> HttpErrorCode.TLS_NEGOTIATION_ERROR
    else -> HttpErrorCode.SDK_UNKNOWN
}

private fun Exception.isConnectTimeoutException(): Boolean =
    findCauseOrSuppressed<SocketTimeoutException>()?.message?.contains("connect", ignoreCase = true) == true
private inline fun <reified T> Exception.isCauseOrSuppressed(): Boolean = findCauseOrSuppressed<T>() != null
private inline fun <reified T> Exception.findCauseOrSuppressed(): T? {
    if (this is T) return this
    if (cause is T) return cause as T
    return suppressedExceptions.firstOrNull { it is T } as? T
}
