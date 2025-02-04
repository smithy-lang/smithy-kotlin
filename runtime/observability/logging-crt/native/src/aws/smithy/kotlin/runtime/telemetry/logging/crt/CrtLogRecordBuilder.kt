/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.logging.crt

import aws.smithy.kotlin.runtime.telemetry.logging.LogLevel
import aws.smithy.kotlin.runtime.telemetry.logging.LogRecordBuilder
import aws.smithy.kotlin.runtime.telemetry.logging.MessageSupplier

public class CrtLogRecordBuilder(
    private val delegate: CrtLogger,
    private val level: LogLevel
) : LogRecordBuilder {
    private var cause: Throwable? = null
    private var msg: (() -> String)? = null

    override fun setCause(ex: Throwable) {
        cause = ex
    }

    override fun setMessage(message: String) {
        msg = { message }
    }

    override fun setMessage(message: MessageSupplier) {
        msg = message
    }

    // CRT logger does not support setting key-value pairs
    override fun setKeyValuePair(key: String, value: Any) { }

    override fun emit() {
        val message = requireNotNull(msg) { "no message provided to LogRecordBuilder" }

        val logMethod = when(level) {
            LogLevel.Trace -> delegate::trace
            LogLevel.Debug -> delegate::debug
            LogLevel.Info -> delegate::info
            LogLevel.Warning -> delegate::warn
            LogLevel.Error -> delegate::error
        }

        logMethod(cause, message)
    }
}