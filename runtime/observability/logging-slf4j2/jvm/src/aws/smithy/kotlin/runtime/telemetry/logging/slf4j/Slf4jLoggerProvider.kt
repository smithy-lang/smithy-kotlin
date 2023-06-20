/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.logging.slf4j

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.telemetry.logging.*
import aws.smithy.kotlin.runtime.time.Instant
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import org.slf4j.spi.LoggingEventBuilder

/**
 * SLF4J 2 based logger provider
 */
@InternalApi
public object Slf4jLoggerProvider : LoggerProvider {
    override fun getOrCreateLogger(name: String): Logger {
        val sl4fjLogger = LoggerFactory.getLogger(name)
        return Sl44JLoggerAdapter(sl4fjLogger)
    }
}

private class Sl44JLoggerAdapter(private val delegate: org.slf4j.Logger) : Logger {
    private fun logWith(t: Throwable?, msg: () -> String, builder: LoggingEventBuilder) =
        builder.setMessage(msg)
            .apply {
                if (t != null) {
                    setCause(t)
                }
            }.log()
    override fun trace(t: Throwable?, msg: () -> String) = logWith(t, msg, delegate.atTrace())
    override fun debug(t: Throwable?, msg: () -> String) = logWith(t, msg, delegate.atDebug())
    override fun info(t: Throwable?, msg: () -> String) = logWith(t, msg, delegate.atInfo())
    override fun warn(t: Throwable?, msg: () -> String) = logWith(t, msg, delegate.atWarn())
    override fun error(t: Throwable?, msg: () -> String) = logWith(t, msg, delegate.atError())
    override fun isEnabledFor(level: LogLevel): Boolean =
        delegate.isEnabledForLevel(level.slf4jLevel)

    override fun atLevel(level: LogLevel): LogRecordBuilder =
        Slf4jLogRecordBuilderAdapter(delegate.atLevel(level.slf4jLevel))
}

private class Slf4jLogRecordBuilderAdapter(
    private val delegate: LoggingEventBuilder,
) : LogRecordBuilder {
    override fun setTimestamp(ts: Instant) { }

    override fun setCause(ex: Throwable) {
        delegate.setCause(ex)
    }

    override fun setMessage(message: String) {
        delegate.setMessage(message)
    }

    override fun setMessage(message: () -> String) {
        delegate.setMessage(message)
    }

    override fun setKeyValuePair(key: String, value: Any) {
        delegate.addKeyValue(key, value)
    }

    override fun setContext(context: Context) {
        // TODO - add a way to get the current trace context and set the key/value pair on it?
    }

    override fun emit() = delegate.log()
}

private val LogLevel.slf4jLevel: org.slf4j.event.Level
    get() = when (this) {
        LogLevel.Error -> Level.ERROR
        LogLevel.Warning -> Level.WARN
        LogLevel.Info -> Level.INFO
        LogLevel.Debug -> Level.DEBUG
        LogLevel.Trace -> Level.TRACE
    }
