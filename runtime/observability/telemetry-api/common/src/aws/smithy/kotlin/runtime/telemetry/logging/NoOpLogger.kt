/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.logging

import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.time.Instant

internal object NoOpLoggerProvider : LoggerProvider {
    override fun getOrCreateLogger(name: String): Logger = NoOpLogger
}

internal object NoOpLogger : Logger {
    override fun trace(t: Throwable?, msg: MessageSupplier) {}
    override fun debug(t: Throwable?, msg: MessageSupplier) {}
    override fun info(t: Throwable?, msg: MessageSupplier) {}
    override fun warn(t: Throwable?, msg: MessageSupplier) {}
    override fun error(t: Throwable?, msg: MessageSupplier) {}
    override fun isEnabledFor(level: LogLevel): Boolean = false
    override fun atLevel(level: LogLevel): LogRecordBuilder = NoOpLogRecordBuilder
}

private object NoOpLogRecordBuilder : LogRecordBuilder {
    override fun setCause(ex: Throwable) {}
    override fun setMessage(message: String) {}
    override fun setMessage(message: MessageSupplier) {}
    override fun setKeyValuePair(key: String, value: Any) {}
    override fun setContext(context: Context) {}
    override fun emit() {}
}
