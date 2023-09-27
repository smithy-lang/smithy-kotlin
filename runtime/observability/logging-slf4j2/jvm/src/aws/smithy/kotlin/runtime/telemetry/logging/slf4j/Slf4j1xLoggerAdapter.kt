/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.logging.slf4j

import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.telemetry.logging.LogLevel
import aws.smithy.kotlin.runtime.telemetry.logging.LogRecordBuilder
import aws.smithy.kotlin.runtime.telemetry.logging.LoggerProvider
import org.slf4j.MDC

private val noOpLogRecordBuilder = LoggerProvider.None.getOrCreateLogger("NoOpLogger").atLevel(LogLevel.Error)

/**
 * SLF4J 1.x based logger
 */
internal class Slf4j1xLoggerAdapter(logger: org.slf4j.Logger) : AbstractSlf4jLoggerAdapter(logger) {
    override fun atLevel(level: LogLevel): LogRecordBuilder = if (isEnabledFor(level)) {
        Slf4j1xLogRecordBuilderAdapter(this, level)
    } else {
        noOpLogRecordBuilder
    }
}

private class Slf4j1xLogRecordBuilderAdapter(
    private val delegate: Slf4j1xLoggerAdapter,
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
