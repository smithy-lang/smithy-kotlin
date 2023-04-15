/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

import mu.KLogger
import mu.KotlinLogging

private fun EventLevel.loggerMethod(): (KLogger, () -> Any?) -> Unit = when (this) {
    EventLevel.Fatal,
    EventLevel.Error,
    -> KLogger::error
    EventLevel.Warning -> KLogger::warn
    EventLevel.Info -> KLogger::info
    EventLevel.Debug -> KLogger::debug
    EventLevel.Trace -> KLogger::trace
}

private fun EventLevel.throwableLoggerMethod(): (KLogger, Throwable, () -> Any?) -> Unit = when (this) {
    EventLevel.Fatal,
    EventLevel.Error,
    -> KLogger::error
    EventLevel.Warning -> KLogger::warn
    EventLevel.Info -> KLogger::info
    EventLevel.Debug -> KLogger::debug
    EventLevel.Trace -> KLogger::trace
}

/**
 * A [TraceProbe] that sends logs events to downstream logging libraries (e.g., Slf4j on JVM). Those downstream
 * libraries may require configuration before messages will actually appear in logs.
 */
public object LoggingTraceProbe : TraceProbe {
    private fun log(spanId: String, event: TraceEvent, message: TraceEventData.Message) {
        val logger = KotlinLogging.logger(event.sourceComponent)
        when (message.exception) {
            null -> event.level.loggerMethod()(logger) { "$spanId: ${message.content()}" }
            else -> event.level.throwableLoggerMethod()(logger, message.exception) { "$spanId: ${message.content()}" }
        }
    }

    override fun postEvent(span: TraceSpan, event: TraceEvent) {
        if (event.data is TraceEventData.Message) log(span.hierarchicalId, event, event.data)
    }

    override fun spanCreated(span: TraceSpan) { }
    override fun spanClosed(span: TraceSpan) { }
}

private val TraceSpan.hierarchicalId: String
    get() = (parent?.let { "${it.hierarchicalId}/" } ?: "") + id
