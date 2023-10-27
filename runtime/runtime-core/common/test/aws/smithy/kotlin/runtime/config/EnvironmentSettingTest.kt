/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.config

import aws.smithy.kotlin.runtime.util.PlatformEnvironProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EnvironmentSettingTest {
    @Test
    fun itResolvesSysPropSettingFirst() {
        val setting = strEnvSetting("foo.bar", "FOO_BAR").orElse("error")
        val testPlatform = mockPlatform(mapOf("foo.bar" to "success"), mapOf("FOO_BAR" to "error"))

        val actual = setting.resolve(testPlatform)
        assertEquals("success", actual)
    }

    @Test
    fun itResolvesEnvVarSettingSecond() {
        val setting = strEnvSetting("foo.bar", "FOO_BAR").orElse("error")
        val testPlatform = mockPlatform(envVars = mapOf("FOO_BAR" to "success"))

        val actual = setting.resolve(testPlatform)
        assertEquals("success", actual)
    }

    @Test
    fun itResolvesDefaultSettingThird() {
        val setting = strEnvSetting("foo.bar", "FOO_BAR").orElse("success")
        val testPlatform = mockPlatform()

        val actual = setting.resolve(testPlatform)
        assertEquals("success", actual)
    }

    @Test
    fun itReturnsNullWithNoValue() {
        val setting = strEnvSetting("foo.bar", "FOO_BAR")
        val testPlatform = mockPlatform()

        assertNull(setting.resolve(testPlatform))
    }

    @Test
    fun itResolvesBooleans() {
        val setting = boolEnvSetting("foo.bar", "FOO_BAR")
        val testPlatform = mockPlatform(mapOf("foo.bar" to "TRUE"))

        val actual = setting.resolve(testPlatform)
        assertEquals(true, actual)
    }

    @Test
    fun itResolvesIntegers() {
        val setting = intEnvSetting("foo.bar", "FOO_BAR")
        val testPlatform = mockPlatform(mapOf("foo.bar" to "42"))

        val actual = setting.resolve(testPlatform)
        assertEquals(42, actual)
    }

    @Test
    fun itResolvesLongs() {
        val setting = longEnvSetting("foo.bar", "FOO_BAR")
        val testPlatform = mockPlatform(mapOf("foo.bar" to "-4294967296"))

        val actual = setting.resolve(testPlatform)
        assertEquals(-4294967296L, actual)
    }

    @Test
    fun itResolvesEnums() {
        val setting = enumEnvSetting<Suit>("foo.bar", "FOO_BAR")
        val testPlatform = mockPlatform(mapOf("foo.bar" to "spades"))

        val actual = setting.resolve(testPlatform)
        assertEquals(Suit.Spades, actual)
    }
}

private fun mockPlatform(sysProps: Map<String, String> = mapOf(), envVars: Map<String, String> = mapOf()) =
    object : PlatformEnvironProvider {
        override fun getAllEnvVars(): Map<String, String> = envVars
        override fun getenv(key: String): String? = envVars[key]
        override fun getAllProperties(): Map<String, String> = sysProps
        override fun getProperty(key: String): String? = sysProps[key]
    }

enum class Suit {
    Hearts,
    Diamonds,
    Clubs,
    Spades,
}
