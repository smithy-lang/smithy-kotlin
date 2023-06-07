/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.logging

/**
 * Internal logging facade
 */
public interface Logger {
    /**
     * Lazy add a log message if trace logging is enabled
     */
    public fun trace(msg: () -> Any)

    /**
     * Lazy add a log message if debug logging is enabled
     */
    public fun debug(msg: () -> Any)

    /**
     * Lazy add a log message if info logging is enabled
     */
    public fun info(msg: () -> Any)

    /**
     * Lazy add a log message if warn logging is enabled
     */
    public fun warn(msg: () -> Any)

    /**
     * Lazy add a log message if error logging is enabled
     */
    public fun error(msg: () -> Any)

    /**
     * Lazy add a log message with throwable payload if trace logging is enabled
     */
    public fun trace(t: Throwable?, msg: () -> Any)

    /**
     * Lazy add a log message with throwable payload if debug logging is enabled
     */
    public fun debug(t: Throwable?, msg: () -> Any)

    /**
     * Lazy add a log message with throwable payload if info logging is enabled
     */
    public fun info(t: Throwable?, msg: () -> Any)

    /**
     * Lazy add a log message with throwable payload if warn logging is enabled
     */
    public fun warn(t: Throwable?, msg: () -> Any?)

    /**
     * Lazy add a log message with throwable payload if error logging is enabled
     */
    public fun error(t: Throwable?, msg: () -> Any)

    /**
     * Test if this logger is enabled for [level]
     * @param level the level to check
     */
    public fun isEnabledFor(level: LogLevel): Boolean

    /**
     * Create a new log record using the returned [LogRecordBuilder]
     * @return a [LogRecordBuilder] that can be used to manually construct an event
     */
    public fun logRecordBuilder(): LogRecordBuilder
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
