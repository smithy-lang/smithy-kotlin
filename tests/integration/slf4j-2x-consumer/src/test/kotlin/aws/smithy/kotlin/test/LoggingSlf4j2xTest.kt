/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.test

import aws.smithy.kotlin.runtime.telemetry.logging.LogLevel
import aws.smithy.kotlin.runtime.telemetry.logging.getLogger
import aws.smithy.kotlin.runtime.telemetry.logging.slf4j.Slf4jLoggerProvider
import aws.smithy.kotlin.runtime.telemetry.logging.warn
import kotlin.test.Test

class LoggingSlf4j2xTest {

    @Test
    fun testSlf4j2xLogging() {
        val logger = Slf4jLoggerProvider.getLogger<LoggingSlf4j2xTest>()
        logger.warn("test 1")
        logger.warn { "test 2" }

        val ex = RuntimeException("testing exception")
        logger.warn(ex) { "test 3" }

        logger.atLevel(LogLevel.Warning).apply {
            setMessage("test 4")
            setKeyValuePair("key1", "value1")
        }.emit()

        logger.atLevel(LogLevel.Warning).apply {
            setMessage("test 5")
            setKeyValuePair("key2", "value2")
            setCause(ex)
        }.emit()
    }
}
