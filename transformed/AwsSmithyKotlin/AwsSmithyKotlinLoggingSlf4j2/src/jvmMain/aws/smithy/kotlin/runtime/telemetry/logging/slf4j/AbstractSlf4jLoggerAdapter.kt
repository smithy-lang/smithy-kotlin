/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.logging.slf4j

import aws.smithy.kotlin.runtime.telemetry.logging.LogLevel
import aws.smithy.kotlin.runtime.telemetry.logging.Logger

/**
 * Common functionality across SLF4J 1.x and 2.x
 * @param delegate the underlying slf4j logger instance
 */
internal abstract class AbstractSlf4jLoggerAdapter(protected val delegate: org.slf4j.Logger) : Logger {
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
}
