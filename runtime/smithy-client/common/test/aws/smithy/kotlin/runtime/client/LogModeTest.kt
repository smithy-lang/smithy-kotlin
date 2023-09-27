/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.client

import aws.smithy.kotlin.runtime.ClientException
import kotlin.test.*

class LogModeTest {
    @Test
    fun testPlus() {
        var mode: LogMode = LogMode.LogRequest
        assertFalse(mode.isEnabled(LogMode.LogResponse))
        mode += LogMode.LogResponse
        assertTrue(mode.isEnabled(LogMode.LogResponse))
    }

    @Test
    fun testMinus() {
        var mode: LogMode = LogMode.LogRequest + LogMode.LogResponse
        assertTrue(mode.isEnabled(LogMode.LogResponse))
        mode -= LogMode.LogResponse
        assertFalse(mode.isEnabled(LogMode.LogResponse))
    }

    @Test
    fun testToString() {
        val mode = LogMode.allModes().reduce { acc, curr -> acc + curr }
        assertTrue { LogMode.allModes().all { mode.isEnabled(it) } }
        val expected = "LogRequest|LogRequestWithBody|LogResponse|LogResponseWithBody"
        assertEquals(expected, mode.toString())
    }

    @Test
    fun testFromString() {
        assertEquals(LogMode.fromString("LogRequest"), LogMode.LogRequest)
    }

    @Test
    fun testFromStringComposite() {
        assertEquals(
            LogMode.fromString("LogRequest|LogRequestWithBody|LogResponse"),
            (LogMode.LogRequest + LogMode.LogRequestWithBody + LogMode.LogResponse),
        )
    }

    @Test
    fun testUnsupportedLogMode() {
        assertFailsWith<ClientException> { LogMode.fromString("UnsupportedLogMode") }
    }

    @Test
    fun testUnsupportedCompositeLogMode() {
        assertFailsWith<ClientException> { LogMode.fromString("LogRequest|UnsupportedLogMode") }
    }
}
