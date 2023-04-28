/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

import aws.smithy.kotlin.runtime.util.AttributeKey
import aws.smithy.kotlin.runtime.util.get
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

// FIXME - convert to class to allow some configuration of how log messages are emitted (e.g. full with attributes vs no attributes, etc)

/**
 * A [TraceProbe] that sends logs events to downstream logging libraries (e.g., Slf4j on JVM). Those downstream
 * libraries may require configuration before messages will actually appear in logs.
 */
public object LoggingTraceProbe : TraceProbe {
    override fun postEvent(span: TraceSpanData, event: TraceEvent) {
        val message = event.data as? TraceEventData.Log ?: return
        val logger = KotlinLogging.logger(message.sourceComponent)

        // FIXME - predicate on level and return early if not enabled (missing in KotlinLogging 3.x)
        // if (!logger.isEnabledForLevel(event.kotlinLoggingLevel)) return

        // Example output
        when (message.exception) {
            null -> event.level.loggerMethod()(logger) { formatEvent(span, message) }
            else -> event.level.throwableLoggerMethod()(logger, message.exception) { formatEvent(span, message) }
        }
    }

    private fun formatEvent(span: TraceSpanData, data: TraceEventData.Log): String =
        // SpanName[traceId=xx, spanId=yy[, a1=v1, a2=v2, ...]: message
        span.attributes.keys.joinToString(
            prefix = "${span.name}[traceId=${span.context.traceId}, spanId=${span.context.spanId}",
            postfix = "]: ${data.content()}",
            separator = "",
        ) {
            @Suppress("UNCHECKED_CAST")
            val value = (span.attributes[it as AttributeKey<Any>])
            ", ${it.name}=$value"
        }

    override fun spanCreated(span: TraceSpan) { }
    override fun spanClosed(span: TraceSpan) { }
}
