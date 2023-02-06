/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.httptest

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.content.ByteArrayContent
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine
import aws.smithy.kotlin.runtime.http.readAll
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.util.encodeBase64String
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/**
 * An [HttpClientEngine] implementation that can be used to record requests and responses from a "live" connection
 * by wrapping an existing engine. This is useful for capturing actual responses from a service, dumping them to
 * json via [toJson] and then later being able to replay them as part of a test connection
 * (see [TestConnection.fromJson]).
 *
 * NOTE: This will not likely work correctly for streaming requests and responses. This is mostly for simple
 * request-response pairs where the body can be read all at once.
 *
 * @param wrapped The "real" engine to wrap and record requests and responses from
 */
@InternalApi
public class RecordingEngine(private val wrapped: HttpClientEngine) : HttpClientEngine by wrapped {
    private val captured = mutableListOf<HttpCall>()

    private suspend fun copyHttpBody(name: String, body: HttpBody): HttpBody = when (body) {
        is HttpBody.Empty -> HttpBody.Empty
        else -> {
            val bytes = body.readAll() ?: error("failed to copy $name body")
            ByteArrayContent(bytes)
        }
    }

    override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall {
        // copy request and response bodies to bytes content so that it can be read multiple times
        val reqBody = copyHttpBody("request", request.body)
        val requestCopy = HttpRequest(method = request.method, url = request.url, headers = request.headers, body = reqBody)
        val call = wrapped.roundTrip(context, request)
        val respBody = copyHttpBody("response", call.response.body)
        val responseCopy = call.response.copy(body = respBody)
        val copy = call.copy(request = requestCopy, response = responseCopy)
        captured.add(copy)
        return copy
    }

    private fun HttpBody.toBytesOrNull(): ByteArray? = when (this) {
        is HttpBody.Bytes -> this.bytes()
        else -> null
    }

    private fun JsonObjectBuilder.putHeaders(headers: Headers) {
        if (headers.isEmpty()) return
        putJsonObject("headers") {
            headers.forEach { key, values ->
                if (values.size <= 1) {
                    put(key, values.first())
                } else {
                    put(key, buildJsonArray { values.forEach { add(it) } })
                }
            }
        }
    }

    private fun JsonObjectBuilder.putBody(body: HttpBody, isBinary: Boolean = false) {
        body.toBytesOrNull()?.let { content ->
            if (isBinary) {
                put("body", content.encodeBase64String())
                put("bodyContentType", "binary")
            } else {
                put("body", content.decodeToString())
            }
        }
    }

    private fun HttpRequest.toJson(): JsonObject = buildJsonObject {
        put("method", method.name)
        put("uri", url.toString())
        putHeaders(headers)
        val isBinary = headers.contains("Content-Type", "application/octet-stream")
        putBody(body, isBinary)
    }

    private fun HttpResponse.toJson(): JsonObject = buildJsonObject {
        put("status", status.value)
        putHeaders(headers)
        val isBinary = headers.contains("Content-Type", "application/octet-stream")
        putBody(body, isBinary)
    }

    private fun HttpCall.toJson(): JsonObject = buildJsonObject {
        put("request", request.toJson())
        put("response", response.toJson())
    }

    public fun toJson(): String {
        val arr = buildJsonArray {
            captured.forEach {
                add(it.toJson())
            }
        }

        val json = Json { prettyPrint = true }
        return json.encodeToString(arr)
    }
}
