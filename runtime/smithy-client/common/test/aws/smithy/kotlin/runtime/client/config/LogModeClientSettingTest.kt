/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.client.config

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.client.LogMode
import aws.smithy.kotlin.runtime.config.resolve
import aws.smithy.kotlin.runtime.util.TestPlatformProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class LogModeClientSettingTest {

    @Test
    fun itResolvesLogModeFromEnvironmentVariables() = runTest {
        val expectedLogMode = LogMode.LogRequest

        val platform = TestPlatformProvider(
            env = mapOf(ClientSettings.LogMode.envVar to expectedLogMode.toString()),
        )

        assertEquals(expectedLogMode, ClientSettings.LogMode.resolve(platform))
    }

    @Test
    fun itResolvesLogModeFromSystemProperties() = runTest {
        val expectedLogMode = LogMode.LogRequestWithBody + LogMode.LogResponseWithBody
        val platform = TestPlatformProvider(
            props = mapOf(ClientSettings.LogMode.sysProp to "LogRequestWithBody|LogResponseWithBody"),
        )

        assertEquals(expectedLogMode, ClientSettings.LogMode.resolve(platform))
    }

    @Test
    fun itThrowsOnInvalidLogModeFromenvVar() = runTest {
        val platform = TestPlatformProvider(
            env = mapOf(ClientSettings.LogMode.envVar to "InvalidLogMode"),
        )
        assertFailsWith<ClientException> { ClientSettings.LogMode.resolve(platform) }
    }

    @Test
    fun itThrowsOnInvalidLogModeFromSystemProperty() = runTest {
        val platform = TestPlatformProvider(
            props = mapOf(ClientSettings.LogMode.sysProp to "InvalidLogMode"),
        )
        assertFailsWith<ClientException> { ClientSettings.LogMode.resolve(platform) }
    }

    @Test
    fun itResolvesNonLowercaseLogModesFromEnvironmentVariables() = runTest {
        val expectedLogMode = LogMode.LogRequest
        val nonLowercaseLogMode = "lOgReQUEST"

        val platform = TestPlatformProvider(
            env = mapOf(
                ClientSettings.LogMode.envVar to nonLowercaseLogMode,
            ),
        )

        assertEquals(expectedLogMode, ClientSettings.LogMode.resolve(platform))
    }

    @Test
    fun itResolvesNonLowercaseLogModesFromSystemProperty() = runTest {
        val expectedLogMode = LogMode.allModes().reduce { acc, logMode -> acc + logMode }
        val nonLowercaseLogMode = "LOGREQUest|logReSponSe|logREQUESTwithBODY|LoGrEsPoNsEWitHBoDY"

        val platform = TestPlatformProvider(
            props = mapOf(
                ClientSettings.LogMode.sysProp to nonLowercaseLogMode,
            ),
        )

        assertEquals(expectedLogMode, ClientSettings.LogMode.resolve(platform))
    }

    @Test
    fun itResolvesWithenvVarPriority() = runTest {
        // set the system property and environment variable. resolution should prioritize system property
        val platform = TestPlatformProvider(
            env = mapOf(
                ClientSettings.LogMode.envVar to "invalid-sdk-log-mode-should-be-ignored",
            ),
            props = mapOf(
                ClientSettings.LogMode.sysProp to "LogRequest",
            ),
        )

        assertEquals(LogMode.LogRequest, ClientSettings.LogMode.resolve(platform))
    }

    @Test
    fun itUsesDefaultLogModeWhenNoneAreConfigured() = runTest {
        val expectedLogMode = LogMode.Default

        val platform = TestPlatformProvider() // no environment variables / system properties / profile

        assertEquals(expectedLogMode, ClientSettings.LogMode.resolve(platform))
    }
}
