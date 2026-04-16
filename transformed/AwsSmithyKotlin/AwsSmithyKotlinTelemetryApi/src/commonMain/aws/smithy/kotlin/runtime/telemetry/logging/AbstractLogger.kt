/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.logging

/**
 * An abstract implementation of a logger. This class delegates the [trace], [debug], [info], [warn], and [error]
 * methods to a centralized [log] method which accepts [LogLevel] as a parameter. By default, this class uses no-op
 * implementations for all other members unless overridden in a subclass.
 */
public abstract class AbstractLogger : Logger {
    override fun trace(t: Throwable?, msg: MessageSupplier) {
        if (isEnabledFor(LogLevel.Trace)) log(LogLevel.Trace, t, msg)
    }

    override fun debug(t: Throwable?, msg: MessageSupplier) {
        if (isEnabledFor(LogLevel.Debug)) log(LogLevel.Debug, t, msg)
    }

    override fun info(t: Throwable?, msg: MessageSupplier) {
        if (isEnabledFor(LogLevel.Info)) log(LogLevel.Info, t, msg)
    }

    override fun warn(t: Throwable?, msg: MessageSupplier) {
        if (isEnabledFor(LogLevel.Warning)) log(LogLevel.Warning, t, msg)
    }

    override fun error(t: Throwable?, msg: MessageSupplier) {
        if (isEnabledFor(LogLevel.Error)) log(LogLevel.Error, t, msg)
    }

    public open fun log(level: LogLevel, t: Throwable?, msg: MessageSupplier) { }

    override fun isEnabledFor(level: LogLevel): Boolean = false

    override fun atLevel(level: LogLevel): LogRecordBuilder = LogRecordBuilder.None
}
