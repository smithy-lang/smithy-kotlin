/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awscredentials

import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.collections.attributesOf
import aws.smithy.kotlin.runtime.telemetry.TelemetryProvider
import aws.smithy.kotlin.runtime.telemetry.logging.AbstractLogRecordBuilder
import aws.smithy.kotlin.runtime.telemetry.logging.AbstractLogger
import aws.smithy.kotlin.runtime.telemetry.logging.AbstractLoggerProvider
import aws.smithy.kotlin.runtime.telemetry.logging.LogLevel
import aws.smithy.kotlin.runtime.telemetry.logging.LogRecordBuilder
import aws.smithy.kotlin.runtime.telemetry.logging.Logger
import aws.smithy.kotlin.runtime.telemetry.logging.LoggerProvider
import aws.smithy.kotlin.runtime.telemetry.logging.MessageSupplier
import aws.smithy.kotlin.runtime.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * A source whose every resolution is scripted, so the cache under test is the only thing making decisions.
 *
 * [results] is consumed in order and the last entry repeats once exhausted, which keeps a test that only cares about
 * the first two resolutions from having to script the rest. [callCount] is what most assertions are actually about:
 * whether the cache called the source at all.
 */
internal class TestCredentialsProvider(
    private val results: List<Result<Credentials>> = listOf(Result.success(testCredentials())),
    private val onResolve: suspend (Int) -> Unit = {},
) : CloseableCredentialsProvider {
    var callCount = 0
        private set

    var closeCount = 0
        private set

    /** The attributes the cache passed down on the most recent call. */
    var lastAttributes: Attributes? = null
        private set

    override suspend fun resolve(attributes: Attributes): Credentials {
        val index = callCount
        callCount++
        lastAttributes = attributes
        onResolve(index)
        return results[minOf(index, results.lastIndex)].getOrThrow()
    }

    override fun close() {
        closeCount++
    }
}

/** A source that is not [Closeable], for the paths that must not assume one. */
internal class PlainCredentialsProvider(
    private val credentials: Credentials = testCredentials(),
) : CredentialsProvider {
    var callCount = 0
        private set

    override suspend fun resolve(attributes: Attributes): Credentials {
        callCount++
        return credentials
    }
}

internal fun testCredentials(
    accessKeyId: String = "AKID",
    expiration: Instant? = null,
    behavior: CredentialsRefreshBehavior? = null,
    providerName: String? = null,
): Credentials = Credentials(
    accessKeyId = accessKeyId,
    secretAccessKey = "secret",
    sessionToken = null,
    expiration = expiration,
    providerName = providerName,
    attributes = behavior?.let { attributesOf { CredentialsRefreshBehaviorKey to it } },
)

/** Jitter pinned to fixed values so backoff assertions name an exact instant. */
internal class TestJitter(
    private val backoff: Duration = 5.minutes,
    private val errorTtl: Duration = 2_000.milliseconds,
) : RefreshJitter {
    override fun refreshBackoff(): Duration = backoff
    override fun errorCacheTtl(): Duration = errorTtl
}

/**
 * Captures log records so the customer-facing diagnostics can be asserted on.
 *
 * The failed-refresh warning is part of what this feature promises, including the source error and the seconds until
 * the next attempt, so it is worth a test: `warn(ex) { }` that drops `ex` still compiles and still logs.
 */
internal class RecordingLoggerProvider : AbstractLoggerProvider() {
    val records = mutableListOf<LogRecord>()

    data class LogRecord(val level: LogLevel, val message: String, val cause: Throwable?)

    override fun getOrCreateLogger(name: String): Logger = RecordingLogger()

    fun recordsAt(level: LogLevel): List<LogRecord> = records.filter { it.level == level }

    // `CoroutineContext.log` emits through atLevel/LogRecordBuilder rather than through the level methods, so a
    // recorder that only overrides warn/error captures nothing.
    private inner class RecordingLogger : AbstractLogger() {
        override fun isEnabledFor(level: LogLevel): Boolean = true

        override fun log(level: LogLevel, t: Throwable?, msg: MessageSupplier) {
            records.add(LogRecord(level, msg(), t))
        }

        override fun atLevel(level: LogLevel): LogRecordBuilder = RecordingLogRecordBuilder(level)
    }

    private inner class RecordingLogRecordBuilder(private val level: LogLevel) : AbstractLogRecordBuilder() {
        private var message: MessageSupplier = { "" }
        private var cause: Throwable? = null

        override fun setCause(ex: Throwable) {
            cause = ex
        }

        override fun setMessage(message: String) {
            this.message = { message }
        }

        override fun setMessage(message: MessageSupplier) {
            this.message = message
        }

        override fun emit() {
            records.add(LogRecord(level, message(), cause))
        }
    }
}

internal class RecordingTelemetryProvider(
    override val loggerProvider: LoggerProvider,
) : TelemetryProvider by TelemetryProvider.None
