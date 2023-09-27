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

    private val useSlf4j2x = try {
        Class.forName("org.slf4j.spi.LoggingEventBuilder")
        true
    } catch (ex: ClassNotFoundException) {
        LoggerFactory.getLogger(Slf4jLoggerProvider::class.java).warn("falling back to SLF4J 1.x compatible binding")
        false
    }

    override fun getOrCreateLogger(name: String): Logger {
        val sl4fjLogger = LoggerFactory.getLogger(name)
        return if (useSlf4j2x) {
            Slf4j2xLoggerAdapter(sl4fjLogger)
        } else {
            Slf4j1xLoggerAdapter(sl4fjLogger)
        }
    }
}
