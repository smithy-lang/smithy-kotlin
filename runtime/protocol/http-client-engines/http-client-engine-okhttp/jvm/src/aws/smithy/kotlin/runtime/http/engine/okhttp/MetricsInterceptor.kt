/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.telemetry.metrics.MonotonicCounter
import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.attributesOf
import okhttp3.*
import okio.*

/**
 * Instrument the HTTP throughput metrics (e.g. bytes rcvd/sent)
 */
internal object MetricsInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val metrics = originalRequest.tag<SdkRequestTag>()?.metrics ?: return chain.proceed(originalRequest)

        val attrs = attributesOf { "server.address" to "${originalRequest.url.host}:${originalRequest.url.port}" }
        val request = if (originalRequest.body != null) {
            originalRequest.newBuilder()
                .method(originalRequest.method, originalRequest.body?.instrument(metrics.bytesSent, attrs))
                .build()
        } else {
            originalRequest
        }

        val originalResponse = chain.proceed(request)
        val response = if (originalResponse.body.contentLength() != 0L) {
            originalResponse.newBuilder()
                .body(originalResponse.body.instrument(metrics.bytesReceived, attrs))
                .build()
        } else {
            originalResponse
        }

        return response
    }
}

internal class InstrumentedSink(
    private val delegate: BufferedSink,
    private val counter: MonotonicCounter,
    private val attributes: Attributes,
) : Sink by delegate {
    override fun write(source: Buffer, byteCount: Long) {
        delegate.write(source, byteCount)
        counter.add(byteCount, attributes)
    }
    override fun close() {
        delegate.emit()
        delegate.close()
    }
}

internal class InstrumentedRequestBody(
    private val delegate: RequestBody,
    private val counter: MonotonicCounter,
    private val attributes: Attributes,
) : RequestBody() {
    override fun contentType(): MediaType? = delegate.contentType()
    override fun isOneShot(): Boolean = delegate.isOneShot()
    override fun isDuplex(): Boolean = delegate.isDuplex()
    override fun contentLength(): Long = delegate.contentLength()
    override fun writeTo(sink: BufferedSink) {
        val metricsSink = InstrumentedSink(sink, counter, attributes).buffer()
        delegate.writeTo(metricsSink)
        if (metricsSink.isOpen) {
            // ensure any buffered data is emitted to the real sink
            metricsSink.emit()
        }
    }
}

internal fun RequestBody.instrument(counter: MonotonicCounter, attributes: Attributes): RequestBody =
    InstrumentedRequestBody(this, counter, attributes)

internal class InstrumentedSource(
    private val delegate: Source,
    private val counter: MonotonicCounter,
    private val attributes: Attributes,
) : Source by delegate {
    override fun timeout(): Timeout = delegate.timeout()
    override fun read(sink: Buffer, byteCount: Long): Long {
        val rc = delegate.read(sink, byteCount)
        if (rc > 0L) {
            counter.add(rc, attributes)
        }
        return rc
    }
    override fun close() {
        delegate.close()
    }
}

internal class InstrumentedResponseBody(
    private val delegate: ResponseBody,
    private val counter: MonotonicCounter,
    private val attributes: Attributes,
) : ResponseBody() {
    override fun contentType(): MediaType? = delegate.contentType()
    override fun contentLength(): Long = delegate.contentLength()
    override fun source(): BufferedSource =
        InstrumentedSource(delegate.source(), counter, attributes).buffer()
}

internal fun ResponseBody.instrument(counter: MonotonicCounter, attributes: Attributes): ResponseBody =
    InstrumentedResponseBody(this, counter, attributes)
