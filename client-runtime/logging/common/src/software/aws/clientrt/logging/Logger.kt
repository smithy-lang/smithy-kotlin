/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.logging

import software.aws.clientrt.util.InternalAPI

/**
 * Internal logging facade
 */
@InternalAPI
public interface Logger {
    companion object {
        /**
         * Get the logger for the class [T]
         */
        inline fun <reified T> getLogger(): Logger {
            return getLogger(requireNotNull(T::class.qualifiedName) { "getLogger<T> cannot be used on an anonymous object" })
        }

        /**
         * Get the logger for the given [name]
         */
        fun getLogger(name: String): Logger {
            return KotlinLoggingAdapter(name)
        }
    }

    /**
     * Lazy add a log message if trace logging is enabled
     */
    public fun trace(msg: () -> Any?)

    /**
     * Lazy add a log message if debug logging is enabled
     */
    public fun debug(msg: () -> Any?)

    /**
     * Lazy add a log message if info logging is enabled
     */
    public fun info(msg: () -> Any?)

    /**
     * Lazy add a log message if warn logging is enabled
     */
    public fun warn(msg: () -> Any?)

    /**
     * Lazy add a log message if error logging is enabled
     */
    public fun error(msg: () -> Any?)

    /**
     * Lazy add a log message with throwable payload if trace logging is enabled
     */
    public fun trace(t: Throwable?, msg: () -> Any?)

    /**
     * Lazy add a log message with throwable payload if debug logging is enabled
     */
    public fun debug(t: Throwable?, msg: () -> Any?)

    /**
     * Lazy add a log message with throwable payload if info logging is enabled
     */
    public fun info(t: Throwable?, msg: () -> Any?)

    /**
     * Lazy add a log message with throwable payload if warn logging is enabled
     */
    public fun warn(t: Throwable?, msg: () -> Any?)

    /**
     * Lazy add a log message with throwable payload if error logging is enabled
     */
    public fun error(t: Throwable?, msg: () -> Any?)

    /**
     * Add a log message indicating an exception will be thrown along with the stack trace.
     */
    public fun <T : Throwable> throwing(throwable: T): T

    /**
     * Add a log message indicating an exception is caught along with the stack trace.
     */
    public fun <T : Throwable> catching(throwable: T)
}

/**
 * Add a log message if trace logging is enabled
 */
public fun Logger.trace(msg: String) = trace { msg }

/**
 * Add a log message if debug logging is enabled
 */
public fun Logger.debug(msg: String) = debug { msg }

/**
 * Add a log message if info logging is enabled
 */
public fun Logger.info(msg: String) = info { msg }

/**
 * Add a log message if warn logging is enabled
 */
public fun Logger.warn(msg: String) = warn { msg }

/**
 * Add a log message if error logging is enabled
 */
public fun Logger.error(msg: String) = error { msg }
