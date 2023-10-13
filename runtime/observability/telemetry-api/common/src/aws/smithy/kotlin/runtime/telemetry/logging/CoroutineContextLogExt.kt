/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.logging

import aws.smithy.kotlin.runtime.ExperimentalApi
import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.telemetry.telemetryProvider
import aws.smithy.kotlin.runtime.telemetry.trace.SpanContext
import aws.smithy.kotlin.runtime.telemetry.trace.traceSpan
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

// NOTE: these keys are specifically chosen to match the ones used by OpenTelemetry
// so that any of the javaagent/autoconfigure MDC instrumentation doesn't result in
// multiple key/value pairs with the same information.
// see: https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/v1.30.0/instrumentation-api-semconv/src/main/java/io/opentelemetry/instrumentation/api/log/LoggingContextConstants.java#L14
private const val TRACE_ID_KEY = "trace_id"
private const val SPAN_ID_KEY = "span_id"

/**
 * Coroutine scoped telemetry context used for carrying telemetry provider configuration
 * @param kvPairs key/value pairs to add to output log statements
 */
@InternalApi
public data class LoggingContextElement(
    val kvPairs: Map<String, Any>,
) : AbstractCoroutineContextElement(LoggingContextElement) {

    public constructor(vararg pairs: Pair<String, Any>) : this(pairs.toMap())
    public companion object Key : CoroutineContext.Key<LoggingContextElement>
    override fun toString(): String = "LoggingContextElement($kvPairs)"
}

@InternalApi
public val CoroutineContext.loggingContext: Map<String, Any>
    get() = get(LoggingContextElement)?.kvPairs ?: emptyMap()

/**
 * Execute [block] with key/value pairs set in the logging context. These will be automatically added
 * to any log record executed via [CoroutineContext.log].
 *
 * @param kvPairs the key/value pairs to add to log records
 * @param block the block of code to execute with the given logging context
 * @return returns the result of executing [block]
 */
@InternalApi
public suspend inline fun<R> withLogCtx(
    vararg kvPairs: Pair<String, Any>,
    crossinline block: suspend () -> R,
): R {
    val ctxMap = coroutineContext.loggingContext.toMutableMap()
    ctxMap.putAll(kvPairs)
    val loggingCtx = LoggingContextElement(ctxMap)
    return withContext(loggingCtx) {
        block()
    }
}

/**
 * Logs a message using the current [LoggerProvider] configured in this [CoroutineContext].
 * @param level The level (or severity) of this event
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
@OptIn(ExperimentalApi::class)
@InternalApi
public fun CoroutineContext.log(
    level: LogLevel,
    sourceComponent: String,
    ex: Throwable? = null,
    content: () -> String,
) {
    val logger = this.telemetryProvider.loggerProvider.getOrCreateLogger(sourceComponent)
    if (!logger.isEnabledFor(level)) return
    val loggingContext = this.loggingContext
    val spanContext = this.traceSpan?.spanContext?.takeIf(SpanContext::isValid)
    logger.atLevel(level)
        .apply {
            ex?.let { setCause(it) }
            setMessage(content)
            loggingContext.forEach { entry -> setKeyValuePair(entry.key, entry.value) }
            if (spanContext != null) {
                setKeyValuePair(TRACE_ID_KEY, spanContext.traceId)
                setKeyValuePair(SPAN_ID_KEY, spanContext.spanId)
            }
        }.emit()
}

/**
 * Logs a message using the current [LoggerProvider] configured in this [CoroutineContext].
 * @param T The class to use for the name of the component that generated the event
 * @param level The level (or severity) of this event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
@InternalApi
public inline fun <reified T> CoroutineContext.log(
    level: LogLevel,
    ex: Throwable? = null,
    noinline content: () -> String,
) {
    val sourceComponent = requireNotNull(T::class.qualifiedName) { "log<T> cannot be used on an anonymous object" }
    log(level, sourceComponent, ex, content)
}

/**
 * Logs an error message using the current [LoggerProvider] configured in this [CoroutineContext].
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
@InternalApi
public fun CoroutineContext.error(sourceComponent: String, ex: Throwable? = null, content: () -> String): Unit =
    log(LogLevel.Error, sourceComponent, ex, content)

/**
 * Logs an error message using the current [LoggerProvider] configured in this [CoroutineContext].
 * @param T The class to use for the name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
@InternalApi
public inline fun <reified T> CoroutineContext.error(ex: Throwable? = null, noinline content: () -> String): Unit =
    log<T>(LogLevel.Error, ex, content)

/**
 * Logs a warning message using the current [LoggerProvider] configured in this [CoroutineContext].
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
@InternalApi
public fun CoroutineContext.warn(sourceComponent: String, ex: Throwable? = null, content: () -> String): Unit =
    log(LogLevel.Warning, sourceComponent, ex, content)

/**
 * Logs a warning message using the current [LoggerProvider] configured in this [CoroutineContext].
 * @param T The class to use for the name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
@InternalApi
public inline fun <reified T> CoroutineContext.warn(ex: Throwable? = null, noinline content: () -> String): Unit =
    log<T>(LogLevel.Warning, ex, content)

/**
 * Logs an info message using the current [LoggerProvider] configured in this [CoroutineContext].
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
@InternalApi
public fun CoroutineContext.info(sourceComponent: String, ex: Throwable? = null, content: () -> String): Unit =
    log(LogLevel.Info, sourceComponent, ex, content)

/**
 * Logs an info message using the current [LoggerProvider] configured in this [CoroutineContext].
 * @param T The class to use for the name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
@InternalApi
public inline fun <reified T> CoroutineContext.info(ex: Throwable? = null, noinline content: () -> String): Unit =
    log<T>(LogLevel.Info, ex, content)

/**
 * Logs a debug message using the current [LoggerProvider] configured in this [CoroutineContext].
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
@InternalApi
public fun CoroutineContext.debug(sourceComponent: String, ex: Throwable? = null, content: () -> String): Unit =
    log(LogLevel.Debug, sourceComponent, ex, content)

/**
 * Logs a debug message using the current [LoggerProvider] configured in this [CoroutineContext].
 * @param T The class to use for the name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
@InternalApi
public inline fun <reified T> CoroutineContext.debug(ex: Throwable? = null, noinline content: () -> String): Unit =
    log<T>(LogLevel.Debug, ex, content)

/**
 * Logs a trace message using the current [LoggerProvider] configured in this [CoroutineContext].
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
@InternalApi
public fun CoroutineContext.trace(sourceComponent: String, ex: Throwable? = null, content: () -> String): Unit =
    log(LogLevel.Trace, sourceComponent, ex, content)

/**
 * Logs a trace message using the current [LoggerProvider] configured in this [CoroutineContext].
 * @param T The class to use for the name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
@InternalApi
public inline fun <reified T> CoroutineContext.trace(ex: Throwable? = null, noinline content: () -> String): Unit =
    log<T>(LogLevel.Trace, ex, content)

/**
 * Get a [Logger] instance using the current [LoggerProvider] configured in this [CoroutineContext]
 * @param sourceComponent The name of the component to create a logger for
 */
@OptIn(ExperimentalApi::class)
@InternalApi
public fun CoroutineContext.logger(sourceComponent: String): Logger {
    val logger = this.telemetryProvider.loggerProvider.getOrCreateLogger(sourceComponent)
    val context = this
    // Pulling a logger instance out disregards additional telemetry context from the coroutine context,
    // wrap the logger in a way that preserves this additional context
    return ContextAwareLogger(context, logger, sourceComponent)
}

private class ContextAwareLogger(
    private val context: CoroutineContext,
    private val delegate: Logger,
    private val sourceComponent: String,
) : Logger {
    override fun trace(t: Throwable?, msg: () -> String) = context.trace(sourceComponent, t, msg)
    override fun debug(t: Throwable?, msg: () -> String) = context.debug(sourceComponent, t, msg)
    override fun info(t: Throwable?, msg: () -> String) = context.info(sourceComponent, t, msg)
    override fun warn(t: Throwable?, msg: () -> String) = context.warn(sourceComponent, t, msg)
    override fun error(t: Throwable?, msg: () -> String) = context.error(sourceComponent, t, msg)
    override fun isEnabledFor(level: LogLevel): Boolean = delegate.isEnabledFor(level)
    override fun atLevel(level: LogLevel): LogRecordBuilder = delegate.atLevel(level)
}

/**
 * Get a [Logger] instance using the current [LoggerProvider] configured in this [CoroutineContext]
 * @param T The class to use for the name of the logger
 */
@InternalApi
public inline fun <reified T> CoroutineContext.logger(): Logger {
    val sourceComponent = requireNotNull(T::class.qualifiedName) { "logger<T> cannot be used on an anonymous object" }
    return logger(sourceComponent)
}
