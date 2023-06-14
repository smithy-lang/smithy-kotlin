/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.logging

import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.util.Attributes

internal object NoOpLoggerProvider : LoggerProvider {
    override fun getOrCreateLogger(name: String): Logger = NoOpLogger
}

private object NoOpLogger : Logger {
    override fun trace(msg: () -> Any) {}

    override fun trace(t: Throwable?, msg: () -> Any) {}

    override fun debug(msg: () -> Any) {}

    override fun debug(t: Throwable?, msg: () -> Any) {}

    override fun info(msg: () -> Any) {}

    override fun info(t: Throwable?, msg: () -> Any) {}

    override fun warn(msg: () -> Any) {}

    override fun warn(t: Throwable?, msg: () -> Any?) {}

    override fun error(msg: () -> Any) {}

    override fun error(t: Throwable?, msg: () -> Any) {}

    override fun isEnabledFor(level: LogLevel): Boolean = false
    override fun logRecordBuilder(): LogRecordBuilder = NoOpLogRecordBuilder
}

private object NoOpLogRecordBuilder : LogRecordBuilder {
    override fun setLevel(level: LogLevel) {}

    override fun setTimestamp(ts: Instant) {}

    override fun setCause(ex: Throwable) {}

    override fun setMessage(message: String) {}

    override fun setMessage(message: () -> Any) {}

    override fun setAttribute(name: String, value: Any) {}

    override fun setAllAttributes(attributes: Attributes) {}

    override fun emit() {}
}
