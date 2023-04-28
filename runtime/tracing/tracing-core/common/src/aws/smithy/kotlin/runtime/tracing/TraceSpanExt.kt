/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

import aws.smithy.kotlin.runtime.io.use
import aws.smithy.kotlin.runtime.logging.Logger
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.emptyAttributes

private class LogEvent(
    override val level: EventLevel,
    val sourceComponent: String,
    override val timestamp: Instant,
    val ex: Throwable? = null,
    val content: () -> Any?,
) : TraceEvent {

    override val attributes: Attributes = emptyAttributes()
    override val data: TraceEventData
        get() = TraceEventData.Log(sourceComponent, ex, content)

    override val name: String = "LogRecord"
}

/**
 * Creates a child span of this [TraceSpan] and passes it to the given [block]. The span is closed when the block
 * completes (regardless of whether the block terminates normally or exceptionally).
 * @param name The name for the new span.
 * @param block The block to execute with the new child span passed as an argument.
 */
public inline fun <T> TraceSpan.withChildSpan(name: String, block: (TraceSpan) -> T): T = child(name).use { block(it) }

/**
 * Logs a message in this [TraceSpan].
 * @param level The level (or severity) of this event
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public fun TraceSpan.log(level: EventLevel, sourceComponent: String, ex: Throwable? = null, content: () -> Any?) {
    val event = LogEvent(
        level,
        sourceComponent,
        Instant.now(),
        ex,
        content,
    )
    postEvent(event)
}

/**
 * Logs a message in this [TraceSpan].
 * @param T The class to use for the name of the component that generated the event
 * @param level The level (or severity) of this event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public inline fun <reified T> TraceSpan.log(level: EventLevel, ex: Throwable? = null, noinline content: () -> Any?) {
    val sourceComponent = requireNotNull(T::class.qualifiedName) { "log<T> cannot be used on an anonymous object" }
    log(level, sourceComponent, ex, content)
}

/**
 * Logs a fatal message in this [TraceSpan].
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public fun TraceSpan.fatal(sourceComponent: String, ex: Throwable? = null, content: () -> Any?): Unit =
    log(EventLevel.Fatal, sourceComponent, ex, content)

/**
 * Logs a fatal message in this [TraceSpan].
 * @param T The class to use for the name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public inline fun <reified T> TraceSpan.fatal(ex: Throwable? = null, noinline content: () -> Any?): Unit =
    log<T>(EventLevel.Fatal, ex, content)

/**
 * Logs an error message in this [TraceSpan].
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public fun TraceSpan.error(sourceComponent: String, ex: Throwable? = null, content: () -> Any?): Unit =
    log(EventLevel.Error, sourceComponent, ex, content)

/**
 * Logs an error message in this [TraceSpan].
 * @param T The class to use for the name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public inline fun <reified T> TraceSpan.error(ex: Throwable? = null, noinline content: () -> Any?): Unit =
    log<T>(EventLevel.Error, ex, content)

/**
 * Logs a warning message in this [TraceSpan].
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public fun TraceSpan.warn(sourceComponent: String, ex: Throwable? = null, content: () -> Any?): Unit =
    log(EventLevel.Warning, sourceComponent, ex, content)

/**
 * Logs a warning message in this [TraceSpan].
 * @param T The class to use for the name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public inline fun <reified T> TraceSpan.warn(ex: Throwable? = null, noinline content: () -> Any?): Unit =
    log<T>(EventLevel.Warning, ex, content)

/**
 * Logs an info message in this [TraceSpan].
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public fun TraceSpan.info(sourceComponent: String, ex: Throwable? = null, content: () -> Any?): Unit =
    log(EventLevel.Info, sourceComponent, ex, content)

/**
 * Logs an info message in this [TraceSpan].
 * @param T The class to use for the name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public inline fun <reified T> TraceSpan.info(ex: Throwable? = null, noinline content: () -> Any?): Unit =
    log<T>(EventLevel.Info, ex, content)

/**
 * Logs a debug message in this [TraceSpan].
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public fun TraceSpan.debug(sourceComponent: String, ex: Throwable? = null, content: () -> Any?): Unit =
    log(EventLevel.Debug, sourceComponent, ex, content)

/**
 * Logs a debug message in this [TraceSpan].
 * @param T The class to use for the name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public inline fun <reified T> TraceSpan.debug(ex: Throwable? = null, noinline content: () -> Any?): Unit =
    log<T>(EventLevel.Debug, ex, content)

/**
 * Logs a trace-level message in this [TraceSpan].
 * @param sourceComponent The name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public fun TraceSpan.trace(sourceComponent: String, ex: Throwable? = null, content: () -> Any?): Unit =
    log(EventLevel.Trace, sourceComponent, ex, content)

/**
 * Logs a trace-level message in this [TraceSpan].
 * @param T The class to use for the name of the component that generated the event
 * @param ex An optional exception which explains the message
 * @param content A lambda which provides the content of the message. This content does not need to include any data
 * from the exception (if any), which may be concatenated later based on probe behavior.
 */
public inline fun <reified T> TraceSpan.trace(ex: Throwable? = null, noinline content: () -> Any?): Unit =
    log<T>(EventLevel.Trace, ex, content)

/**
 * Gets a [Logger] for this [TraceSpan] and the given component name.
 * @param forSourceComponent The name of the component that used in logging events
 * @return A new [Logger] instance
 */
public fun TraceSpan.logger(forSourceComponent: String): Logger = TraceSpanLogger(this, forSourceComponent)

/**
 * Gets a [Logger] for this [TraceSpan]. The [T] type is used as the component name for messages emitted to this logger.
 * @param T The class to use for the name of the component for all messages
 */
public inline fun <reified T> TraceSpan.logger(): Logger {
    val sourceComponent = requireNotNull(T::class.qualifiedName) { "logger<T> cannot be used on an anonymous object" }
    return logger(sourceComponent)
}

private class TraceSpanLogger(private val span: TraceSpan, private val sourceComponent: String) : Logger {
    fun log(level: EventLevel, ex: Throwable? = null, msg: () -> Any?) {
        val event = LogEvent(
            level,
            sourceComponent,
            Instant.now(),
            ex,
            msg,
        )
        span.postEvent(event)
    }

    override fun error(msg: () -> Any?) = log(EventLevel.Error, null, msg)
    override fun error(t: Throwable?, msg: () -> Any?) = log(EventLevel.Error, t, msg)

    override fun warn(msg: () -> Any?) = log(EventLevel.Warning, null, msg)
    override fun warn(t: Throwable?, msg: () -> Any?) = log(EventLevel.Warning, t, msg)

    override fun info(msg: () -> Any?) = log(EventLevel.Info, null, msg)
    override fun info(t: Throwable?, msg: () -> Any?) = log(EventLevel.Info, t, msg)

    override fun debug(msg: () -> Any?) = log(EventLevel.Debug, null, msg)
    override fun debug(t: Throwable?, msg: () -> Any?) = log(EventLevel.Debug, t, msg)

    override fun trace(msg: () -> Any?) = log(EventLevel.Trace, null, msg)
    override fun trace(t: Throwable?, msg: () -> Any?) = log(EventLevel.Trace, t, msg)
}

/**
 * Set common error attributes from an exception
 * @param ex the exception to record attributes for
 */
public fun TraceSpan.recordException(ex: Throwable, escaped: Boolean) {
    // https://opentelemetry.io/docs/reference/specification/trace/semantic_conventions/exceptions/
    val exType = ex::class.qualifiedName ?: ex::class.simpleName
    setAttr("error", true)
    ex.message?.let { setAttr("exception.message", it) }
    exType?.let { setAttr("exception.type", it) }
    setAttr("exception.stacktrace", ex.stackTraceToString())
    ex.cause?.let { setAttr("exception.cause", it.toString()) }
    setAttr("exception.escaped", escaped)
}

/**
 *
 * Records a new event that has occurred within the logical context of this span.
 * @param name the name of the event
 * @param attributes any attributes associated with the event
 * @param timestamp the time the event occurred
 */
public fun TraceSpan.postEvent(
    name: String,
    attributes: Attributes = emptyAttributes(),
    timestamp: Instant = Instant.now(),
) {
    val event = DefaultEvent(name, attributes, timestamp)
    postEvent(event)
}
