/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.logging.slf4j

import aws.smithy.kotlin.runtime.telemetry.logging.*
import org.slf4j.LoggerFactory

/**
 * SLF4J based logger provider
 */
public object Slf4jLoggerProvider : LoggerProvider {

    private val slf4jLoggerAdapter: (org.slf4j.Logger) -> Logger = try {
        Class.forName("org.slf4j.spi.LoggingEventBuilder")
        ::Slf4j2xLoggerAdapter
    } catch (ex: ClassNotFoundException) {
        LoggerFactory.getLogger(Slf4jLoggerProvider::class.java).warn("falling back to SLF4J 1.x compatible binding")
        ::Slf4j1xLoggerAdapter
    }

    override fun getOrCreateLogger(name: String): Logger {
        val sl4fjLogger = LoggerFactory.getLogger(name)
        return slf4jLoggerAdapter(sl4fjLogger)
    }
}
