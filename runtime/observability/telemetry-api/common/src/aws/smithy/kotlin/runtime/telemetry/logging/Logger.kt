/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.logging

public typealias MessageSupplier = () -> String

/**
 * Internal logging facade
 */
public interface Logger {
    public companion object {
        /**
         * A no-op [Logger] that does nothing
         */
        public val None: Logger = NoOpLogger
    }

    /**
     * Lazy add a log message with throwable payload if trace logging is enabled
     */
    public fun trace(t: Throwable? = null, msg: MessageSupplier)

    /**
     * Lazy add a log message with throwable payload if debug logging is enabled
     */
    public fun debug(t: Throwable? = null, msg: MessageSupplier)

    /**
     * Lazy add a log message with throwable payload if info logging is enabled
     */
    public fun info(t: Throwable? = null, msg: MessageSupplier)

    /**
     * Lazy add a log message with throwable payload if warn logging is enabled
     */
    public fun warn(t: Throwable? = null, msg: MessageSupplier)

    /**
     * Lazy add a log message with throwable payload if error logging is enabled
     */
    public fun error(t: Throwable? = null, msg: MessageSupplier)

    /**
     * Test if this logger is enabled for [level]
     * @param level the level to check
     */
    public fun isEnabledFor(level: LogLevel): Boolean

    /**
     * Create a new log record using the returned [LogRecordBuilder]
     * @param level the level to log at
     * @return a [LogRecordBuilder] that can be used to manually construct an event
     */
    public fun atLevel(level: LogLevel): LogRecordBuilder
}

/**
 * Add a log message if trace logging is enabled
 */
public fun Logger.trace(msg: String): Unit = trace { msg }

/**
 * Add a log message if debug logging is enabled
 */
public fun Logger.debug(msg: String): Unit = debug { msg }

/**
 * Add a log message if info logging is enabled
 */
public fun Logger.info(msg: String): Unit = info { msg }

/**
 * Add a log message if warn logging is enabled
 */
public fun Logger.warn(msg: String): Unit = warn { msg }

/**
 * Add a log message if error logging is enabled
 */
public fun Logger.error(msg: String): Unit = error { msg }
