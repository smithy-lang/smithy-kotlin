/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.logging.slf4j

import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.telemetry.logging.LogLevel
import aws.smithy.kotlin.runtime.telemetry.logging.LogRecordBuilder
import org.slf4j.event.Level
import org.slf4j.spi.LoggingEventBuilder

/**
 * SLF4J 2.x based logger
 */
internal class Slf4j2xLoggerAdapter(logger: org.slf4j.Logger) : AbstractSlf4jLoggerAdapter(logger) {
    override fun atLevel(level: LogLevel): LogRecordBuilder =
        Slf4j2xLogRecordBuilderAdapter(delegate.atLevel(level.slf4jLevel))
}

private class Slf4j2xLogRecordBuilderAdapter(
    private val delegate: LoggingEventBuilder,
) : LogRecordBuilder {
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
