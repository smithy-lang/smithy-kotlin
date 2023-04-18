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
    override fun postEvent(span: TraceSpan, event: TraceEvent) {
        val message = event.data as? TraceEventData.Message ?: return
        val logger = KotlinLogging.logger(event.sourceComponent)
        val spanName = span.hierarchicalName

        // TODO - how do we want to display span attributes?
        // example trace output: 10 [main] TRACE aws.smithy.kotlin.runtime.http.operation.SerializeHandler - GetCallerIdentity(traceId=4cd7d508-1c81-4b46-9536-1947a09a0d56) (rpc.method=GetCallerIdentity, rpc.service=STS): request serialized in 6.737141ms
        val attrs = span.attributes.keys.joinToString(prefix = " (", postfix = ")") {
            @Suppress("UNCHECKED_CAST")
            val value = (span.attributes[it as AttributeKey<Any>])
            "${it.name}=$value"
        }.takeIf { span.attributes.keys.isNotEmpty() } ?: ""

        when (message.exception) {
            null -> event.level.loggerMethod()(logger) { "${spanName}$attrs: ${message.content()}" }
            else -> event.level.throwableLoggerMethod()(logger, message.exception) { "${spanName}$attrs: ${message.content()}" }
        }
    }

    override fun spanCreated(span: TraceSpan) { }
    override fun spanClosed(span: TraceSpan) { }
}

private val TraceSpan.hierarchicalName: String
    get() = (parent?.let { "${it.hierarchicalName}/" } ?: "") + metadata.name + if (parent == null) "(traceId=${metadata.traceId})" else ""
