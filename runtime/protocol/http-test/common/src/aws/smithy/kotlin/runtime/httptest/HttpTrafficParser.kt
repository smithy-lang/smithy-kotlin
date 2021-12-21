/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.httptest

import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.content.ByteArrayContent
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.util.decodeBase64Bytes
import kotlinx.serialization.json.*

internal fun parseHttpTraffic(json: String) = buildTestConnection {
    val traffic = Json.parseToJsonElement(json).jsonArray
    for (call in traffic.map { it.jsonObject }) {
        val reqObj = call["request"] ?: error("expected `request` in document")
        val respObj = checkNotNull(call["response"]) { "expected `response` key in document " }.jsonObject

        val req = parseRequest(reqObj)
        val resp = parseResponse(respObj)
        if (req != null) {
            expect(req, resp)
        } else {
            // no assertions made for null requests
            expect(resp)
        }
    }
}

private enum class BodyContentType {
    UTF_8,
    BINARY;
    companion object {
        fun fromValue(value: String): BodyContentType = when (value.lowercase()) {
            "utf8" -> UTF_8
            "binary" -> BINARY
            else -> error("invalid body content type: $value")
        }
    }
}

private fun parseRequest(req: JsonElement): HttpRequest? = when (req) {
    // no assertions will be made
    is JsonNull -> null
    is JsonObject -> {
        val method = req["method"]?.jsonPrimitive?.content ?: "GET"
        val uri = req["uri"]?.jsonPrimitive?.content ?: error("expected `uri` in request")
        val headersObj = req["headers"]?.jsonObject
        // by default assumed to be UTF-8 string.
        val bodyContentType = req["bodyContentType"]?.let { BodyContentType.fromValue(it.jsonPrimitive.content) } ?: BodyContentType.UTF_8
        val body = req["body"]?.jsonPrimitive?.content

        val builder = HttpRequestBuilder()
        builder.method = HttpMethod.parse(method)
        builder.url(Url.parse(uri))

        if (headersObj != null) {
            val headers = convertHeaders(headersObj)
            builder.headers.appendAll(headers)
        }

        if (body != null) {
            builder.body = convertBody(body, bodyContentType)
        }

        builder.build()
    }
    else -> error("expected object or `null` for `request` key")
}

private fun parseResponse(resp: JsonObject): HttpResponse {
    val status = resp["status"]?.jsonPrimitive?.intOrNull ?: error("expected http status in response object")
    // val version = resp["version"]?.jsonPrimitive?.content ?: "HTTP/1.1"
    val headersObj = resp["headers"]?.jsonObject
    val bodyContentType = resp["bodyContentType"]?.let { BodyContentType.fromValue(it.jsonPrimitive.content) } ?: BodyContentType.UTF_8
    val body = resp["body"]?.jsonPrimitive?.content

    val headers = if (headersObj != null) {
        convertHeaders(headersObj)
    } else {
        Headers.Empty
    }

    val httpBody = if (body != null) {
        convertBody(body, bodyContentType)
    } else {
        HttpBody.Empty
    }

    return HttpResponse(HttpStatusCode.fromValue(status), headers, httpBody)
}

private fun convertHeaders(headers: JsonObject): Headers {
    val builder = HeadersBuilder()
    headers.forEach { hdr ->
        when (val value = hdr.value) {
            is JsonPrimitive -> builder.append(hdr.key, value.content)
            is JsonArray -> builder.appendAll(hdr.key, value.map { it.jsonPrimitive.content })
            else -> error("invalid header value for key: `${hdr.key}`")
        }
    }
    return builder.build()
}

private fun convertBody(body: String, bodyContentType: BodyContentType): HttpBody = when (bodyContentType) {
    BodyContentType.UTF_8 -> ByteArrayContent(body.encodeToByteArray())
    BodyContentType.BINARY -> ByteArrayContent(body.decodeBase64Bytes())
}
