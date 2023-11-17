/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.crt

import aws.sdk.kotlin.crt.http.HttpRequestBodyStream
import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.net.url.QueryParameters
import aws.smithy.kotlin.runtime.net.url.Url
import aws.smithy.kotlin.runtime.text.ensurePrefix
import aws.smithy.kotlin.runtime.text.ensureSuffix
import kotlin.coroutines.coroutineContext
import aws.sdk.kotlin.crt.http.Headers as HeadersCrt
import aws.sdk.kotlin.crt.http.HttpRequest as HttpRequestCrt

private suspend fun signableBodyStream(
    body: HttpBody,
    unsignedPayload: Boolean = false,
    awsChunked: Boolean = false,
): HttpRequestBodyStream? {
    if (body.isOneShot || unsignedPayload || awsChunked) return null // can only consume stream once OR unsigned/chunked payload

    return when (body) {
        is HttpBody.Empty -> null
        is HttpBody.Bytes -> HttpRequestBodyStream.fromByteArray(body.bytes())
        is HttpBody.ChannelContent -> ReadChannelBodyStream(body.readFrom(), coroutineContext)
        is HttpBody.SourceContent -> SdkSourceBodyStream(body.readFrom())
    }
}

/**
 * Convert an [HttpRequest] into a CRT HttpRequest for the purposes of signing
 */
@InternalApi
public suspend fun HttpRequest.toSignableCrtRequest(
    unsignedPayload: Boolean = false,
    awsChunked: Boolean = false,
): HttpRequestCrt =
    HttpRequestCrt(
        method = method.name,
        encodedPath = url.requestRelativePath,
        headers = headers.toCrtHeaders(),
        body = signableBodyStream(body, unsignedPayload, awsChunked),
    )

// proxy the smithy-client-rt version of Headers to CRT (which is based on our client-rt version in the first place)
private class HttpHeadersCrt(val headers: HeadersBuilder) : HeadersCrt {
    override fun contains(name: String): Boolean = headers.contains(name)
    override fun entries(): Set<Map.Entry<String, List<String>>> = headers.entries()
    override fun getAll(name: String): List<String>? = headers.getAll(name)
    override fun isEmpty(): Boolean = headers.isEmpty()
    override fun names(): Set<String> = headers.names()
}

/**
 * Update a request builder from a CRT HTTP request (primary use is updating a request builder after signing)
 */
@InternalApi
public fun HttpRequestBuilder.update(crtRequest: HttpRequestCrt) {
    crtRequest.headers.entries().forEach { entry ->
        headers.appendMissing(entry.key, entry.value)
    }

    if (crtRequest.encodedPath.isNotBlank()) {
        crtRequest.queryParameters()?.let {
            it.forEach { (key, values) ->
                // the crt request has a url encoded path which means
                // simply appending missing could result in both the raw and percent-encoded
                // value being present. Instead just append new keys added by signing
                if (key !in url.parameters) {
                    url.parameters.addAll(key, values)
                }
            }
        }
    }
}

/**
 * Get just the query parameters (if any)
 * @return the query parameters from the path or null if there weren't any
 */
@InternalApi
public fun HttpRequestCrt.queryParameters(): QueryParameters? {
    val idx = encodedPath.indexOf("?")
    if (idx < 0 || idx + 1 > encodedPath.length) return null

    val fragmentIdx = encodedPath.indexOf("#", startIndex = idx)
    val rawQueryString = if (fragmentIdx > 0) encodedPath.substring(idx, fragmentIdx) else encodedPath.substring(idx)
    return QueryParameters.parseEncoded(rawQueryString)
}

/**
 * Get just the encoded path sans any query or fragment
 * @return the URI path segment from the encoded path
 */
@InternalApi
public fun HttpRequestCrt.path(): String {
    val idx = encodedPath.indexOf("?")
    return if (idx > 0) encodedPath.substring(0, idx) else encodedPath
}

// Convert CRT header type to SDK header type
@InternalApi
public fun aws.sdk.kotlin.crt.http.Headers.toSdkHeaders(): Headers {
    val headersBuilder = HeadersBuilder()

    forEach { key, values ->
        headersBuilder.appendAll(key, values)
    }

    return headersBuilder.build()
}

// Convert SDK header type to CRT header type
@InternalApi
public fun Headers.toCrtHeaders(): aws.sdk.kotlin.crt.http.Headers {
    val headersBuilder = aws.sdk.kotlin.crt.http.HeadersBuilder()

    forEach { key, values ->
        headersBuilder.appendAll(key, values)
    }

    return headersBuilder.build()
}
