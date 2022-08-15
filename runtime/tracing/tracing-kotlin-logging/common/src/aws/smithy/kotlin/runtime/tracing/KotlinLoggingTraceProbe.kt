/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

import mu.KLogger
import mu.KotlinLogging

/**
 * A [TraceProbe] that logs events to the [kotlin-logging](https://github.com/MicroUtils/kotlin-logging) library.
 * kotlin-logging requires configuration (e.g., of Slf4j on JVM) before events sent to the probe will appear in logs.
 */
public class KotlinLoggingTraceProbe : TraceProbe {
    private fun EventLevel.loggerMethod(): (KLogger, () -> Any?) -> Unit = when (this) {
        EventLevel.Fatal,
        EventLevel.Error, -> KLogger::error
        EventLevel.Warning -> KLogger::warn
        EventLevel.Info -> KLogger::info
        EventLevel.Debug -> KLogger::debug
        EventLevel.Trace -> KLogger::trace
    }

    private fun log(spanId: String, event: TraceEvent) {
        val logger = KotlinLogging.logger(event.sourceComponent)
        val method = event.level.loggerMethod()
        method(logger) {
            val msg = (event.data as TraceEventData.Message).text()
            "$spanId: $msg"
        }
    }

    override fun postEvents(span: TraceSpan, events: Iterable<TraceEvent>) {
        val spanId = span.hierarchicalId
        events.filter { it.data is TraceEventData.Message }.forEach { log(spanId, it) }
    }

    override fun spanClosed(span: TraceSpan) { } // No action necessary
}

private val TraceSpan.hierarchicalId: String
    get() = (parent?.let { "${it.hierarchicalId}/" } ?: "") + id
