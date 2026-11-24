/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.crt

import aws.sdk.kotlin.crt.http.HttpRequestBodyStream
import aws.sdk.kotlin.crt.io.MutableBuffer
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Tracks the sending of a request body to CRT.
 */
internal class RequestSendProgress(private val timeSource: TimeSource = TimeSource.Monotonic) {
    /**
     * Completes once CRT has taken the whole request body
     */
    val sent: CompletableJob = Job()

    private val lastActivity = atomic<TimeMark>(timeSource.markNow())

    /**
     * Record that CRT asked for more body data, which it only does while it can write to the socket
     */
    fun markActivity() {
        lastActivity.value = timeSource.markNow()
    }

    /**
     * How long it has been since CRT last asked for body data (or since sending started)
     */
    val idleTime: Duration
        get() = lastActivity.value.elapsedNow()
}

/**
 * Reports each [sendRequestBody] call and the end of the body to [progress].
 */
internal class ProgressTrackingBodyStream(
    private val delegate: HttpRequestBodyStream,
    private val progress: RequestSendProgress,
) : HttpRequestBodyStream {
    override fun sendRequestBody(buffer: MutableBuffer): Boolean {
        progress.markActivity()
        return delegate.sendRequestBody(buffer).also { done ->
            if (done) progress.sent.complete()
        }
    }

    override fun resetPosition(): Boolean = delegate.resetPosition()
}
