/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.crt.io

import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.telemetry.metrics.MonotonicCounter

private class ReportingByteReadChannel(
    val delegate: SdkByteReadChannel,
    val metric: MonotonicCounter,
) : SdkByteReadChannel by delegate {
    override suspend fun read(sink: SdkBuffer, limit: Long): Long = delegate.read(sink, limit).also {
        if (it > 0) metric.add(it)
    }
}

internal fun SdkByteReadChannel.reportingTo(metric: MonotonicCounter): SdkByteReadChannel =
    ReportingByteReadChannel(this, metric)
