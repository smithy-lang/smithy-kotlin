/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.client

import aws.smithy.kotlin.runtime.ClientException
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SdkLogModeTest {
    @Test
    fun testPlus() {
        var mode: SdkLogMode = SdkLogMode.LogRequest
        assertFalse(mode.isEnabled(SdkLogMode.LogResponse))
        mode += SdkLogMode.LogResponse
        assertTrue(mode.isEnabled(SdkLogMode.LogResponse))
    }

    @Test
    fun testMinus() {
        var mode: SdkLogMode = SdkLogMode.LogRequest + SdkLogMode.LogResponse
        assertTrue(mode.isEnabled(SdkLogMode.LogResponse))
        mode -= SdkLogMode.LogResponse
        assertFalse(mode.isEnabled(SdkLogMode.LogResponse))
    }

    @Test
    fun testToString() {
        val mode = SdkLogMode.allModes().reduce { acc, curr -> acc + curr }
        assertTrue { SdkLogMode.allModes().all { mode.isEnabled(it) } }
        val expected = "LogRequest|LogRequestWithBody|LogResponse|LogResponseWithBody"
        assertEquals(expected, mode.toString())
    }

    @Test
    fun testFromString() {
        assertEquals(SdkLogMode.fromString("LogRequest"), SdkLogMode.LogRequest)
    }

    @Test
    fun testFromStringComposite() {
        assertEquals(
            SdkLogMode.fromString("LogRequest|LogRequestWithBody|LogResponse"),
            (SdkLogMode.LogRequest + SdkLogMode.LogRequestWithBody + SdkLogMode.LogResponse),
        )
    }

    @Test
    fun testUnsupportedSdkLogMode() {
        assertThrows<ClientException> { SdkLogMode.fromString("UnsupportedLogMode") }
    }

    @Test
    fun testUnsupportedCompositeSdkLogMode() {
        assertThrows<ClientException> { SdkLogMode.fromString("LogRequest|UnsupportedLogMode") }
    }
}
