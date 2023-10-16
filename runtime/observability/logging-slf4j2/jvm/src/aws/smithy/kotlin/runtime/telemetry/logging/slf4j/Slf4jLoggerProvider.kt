/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.logging.slf4j

import aws.smithy.kotlin.runtime.telemetry.logging.*
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

/**
 * SLF4J based logger provider
 */
public object Slf4jLoggerProvider : LoggerProvider {
    private var useSlf4j2x: Boolean? = null

    private fun supportsSlf4j2x(logger: org.slf4j.Logger): Boolean {
        if (useSlf4j2x == null) {
            useSlf4j2x = try {
                // Considering Logger#atLevel only exists in slf4j2, this will throw in slf4j1 and proceed without throwing in slf4j2.
                logger.atLevel(Level.DEBUG)
                true
            } catch (e: NoSuchMethodError) {
                LoggerFactory.getLogger(Slf4jLoggerProvider::class.java).warn("falling back to SLF4J 1.x compatible binding")
                false
            }
        }
        return useSlf4j2x!!
    }

    override fun getOrCreateLogger(name: String): Logger {
        val sl4fjLogger = LoggerFactory.getLogger(name)
        return if (supportsSlf4j2x(sl4fjLogger)) {
            Slf4j2xLoggerAdapter(sl4fjLogger)
        } else {
            Slf4j1xLoggerAdapter(sl4fjLogger)
        }
    }
}
