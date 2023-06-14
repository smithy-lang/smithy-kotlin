/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.logging

/**
 * Entry point for creating [Logger] instances
 */
public interface LoggerProvider {
    public companion object {
        /**
         * A no-op [LoggerProvider] that does nothing
         */
        public val None: LoggerProvider = NoOpLoggerProvider

        // TODO - Default/Global logger (JVM -> SLF4J)
    }

    /**
     * Get a logger by name
     *
     * @param name the name of the logger to get or create
     */
    public fun getOrCreateLogger(name: String): Logger
}

/**
 * Get the logger for the class [T]
 */
public inline fun <reified T> LoggerProvider.getLogger(): Logger =
    getOrCreateLogger(requireNotNull(T::class.qualifiedName) { "getLogger<T> cannot be used on an anonymous object" })
