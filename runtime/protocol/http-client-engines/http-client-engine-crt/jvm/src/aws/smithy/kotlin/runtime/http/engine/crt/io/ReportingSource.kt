/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.crt.io

import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.telemetry.metrics.MonotonicCounter

private class ReportingSource(val delegate: SdkSource, val metric: MonotonicCounter) : SdkSource by delegate {
    override fun read(sink: SdkBuffer, limit: Long): Long = delegate.read(sink, limit).also {
        if (it > 0) metric.add(it)
    }
}

internal fun SdkSource.reportingTo(metric: MonotonicCounter): SdkSource = ReportingSource(this, metric)
