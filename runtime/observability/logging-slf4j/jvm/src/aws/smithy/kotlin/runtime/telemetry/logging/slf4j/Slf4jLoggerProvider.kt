/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.logging.slf4j

import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.telemetry.logging.*
import org.slf4j.LoggerFactory
import org.slf4j.MDC

/**
 * SLF4J 1.x based logger provider
 */
public object Slf4jLoggerProvider : LoggerProvider {
    override fun getOrCreateLogger(name: String): Logger {
        val sl4fjLogger = LoggerFactory.getLogger(name)
        return Slf4JLoggerAdapter(sl4fjLogger)
    }
}

private val noOpLogRecordBuilder = LoggerProvider.None.getOrCreateLogger("NoOpLogger").atLevel(LogLevel.Error)

private class Slf4JLoggerAdapter(private val delegate: org.slf4j.Logger) : Logger {
    override fun trace(t: Throwable?, msg: () -> String) {
        if (!isEnabledFor(LogLevel.Trace)) return
        if (t != null) {
            delegate.trace(msg(), t)
        } else {
            delegate.trace(msg())
        }
    }
    override fun debug(t: Throwable?, msg: () -> String) {
        if (!isEnabledFor(LogLevel.Debug)) return
        if (t != null) {
            delegate.debug(msg(), t)
        } else {
            delegate.debug(msg())
        }
    }
    override fun info(t: Throwable?, msg: () -> String) {
        if (!isEnabledFor(LogLevel.Info)) return
        if (t != null) {
            delegate.info(msg(), t)
        } else {
            delegate.info(msg())
        }
    }
    override fun warn(t: Throwable?, msg: () -> String) {
        if (!isEnabledFor(LogLevel.Warning)) return
        if (t != null) {
            delegate.warn(msg(), t)
        } else {
            delegate.warn(msg())
        }
    }
    override fun error(t: Throwable?, msg: () -> String) {
        if (!isEnabledFor(LogLevel.Error)) return
        if (t != null) {
            delegate.error(msg(), t)
        } else {
            delegate.error(msg())
        }
    }
    override fun isEnabledFor(level: LogLevel): Boolean = when (level) {
        LogLevel.Trace -> delegate.isTraceEnabled
        LogLevel.Debug -> delegate.isDebugEnabled
        LogLevel.Info -> delegate.isInfoEnabled
        LogLevel.Warning -> delegate.isWarnEnabled
        LogLevel.Error -> delegate.isErrorEnabled
    }

    override fun atLevel(level: LogLevel): LogRecordBuilder = if (isEnabledFor(level)) {
        Slf4jLogRecordBuilderAdapter(this, level)
    } else {
        noOpLogRecordBuilder
    }
}

private class Slf4jLogRecordBuilderAdapter(
    private val delegate: Slf4JLoggerAdapter,
    private val level: LogLevel,
) : LogRecordBuilder {

    private var msg: (() -> String)? = null
    private var cause: Throwable? = null
    private val kvp by lazy { mutableMapOf<String, Any>() }
    override fun setCause(ex: Throwable) {
        cause = ex
    }

    override fun setMessage(message: String) {
        msg = { message }
    }

    override fun setMessage(message: () -> String) {
        msg = message
    }

    override fun setKeyValuePair(key: String, value: Any) {
        kvp[key] = value
    }

    override fun setContext(context: Context) {
        // TODO - add a way to get the current trace context and set the key/value pair on it?
    }

    override fun emit() {
        val message = requireNotNull(msg) { "no message provided to LogRecordBuilder" }
        val logMethod = when (level) {
            LogLevel.Error -> delegate::error
            LogLevel.Warning -> delegate::warn
            LogLevel.Info -> delegate::info
            LogLevel.Debug -> delegate::debug
            LogLevel.Trace -> delegate::trace
        }

        if (kvp.isNotEmpty()) {
            val prevCtx = MDC.getCopyOfContextMap()
            try {
                kvp.forEach { (k, v) ->
                    MDC.put(k, v.toString())
                }
                logMethod(cause, message)
            } finally {
                MDC.setContextMap(prevCtx)
            }
        } else {
            logMethod(cause, message)
        }
    }
}
