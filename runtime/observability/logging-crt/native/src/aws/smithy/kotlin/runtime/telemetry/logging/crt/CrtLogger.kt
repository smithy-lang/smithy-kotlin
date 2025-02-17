/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.logging.crt

import aws.sdk.kotlin.crt.WithCrt
import aws.sdk.kotlin.crt.log
import aws.smithy.kotlin.runtime.telemetry.logging.LogLevel
import aws.smithy.kotlin.runtime.telemetry.logging.LogRecordBuilder
import aws.smithy.kotlin.runtime.telemetry.logging.Logger
import aws.smithy.kotlin.runtime.telemetry.logging.MessageSupplier
import aws.sdk.kotlin.crt.Config as CrtConfig
import aws.sdk.kotlin.crt.LogLevel as CrtLogLevel

public class CrtLogger(public val name: String, public val config: CrtConfig) :
    WithCrt(),
    Logger {
    override fun trace(t: Throwable?, msg: MessageSupplier): Unit = log(CrtLogLevel.Trace, msg())
    override fun debug(t: Throwable?, msg: MessageSupplier): Unit = log(CrtLogLevel.Debug, msg())
    override fun info(t: Throwable?, msg: MessageSupplier): Unit = log(CrtLogLevel.Info, msg())
    override fun warn(t: Throwable?, msg: MessageSupplier): Unit = log(CrtLogLevel.Warn, msg())
    override fun error(t: Throwable?, msg: MessageSupplier): Unit = log(CrtLogLevel.Error, msg())
    override fun isEnabledFor(level: LogLevel): Boolean = config.logLevel.ordinal >= level.ordinal
    override fun atLevel(level: LogLevel): LogRecordBuilder = CrtLogRecordBuilder(this, level)
}
