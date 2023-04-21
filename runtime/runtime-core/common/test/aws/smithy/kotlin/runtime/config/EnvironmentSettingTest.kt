package aws.smithy.kotlin.runtime.config

import aws.smithy.kotlin.runtime.util.PlatformEnvironProvider
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EnvironmentSettingTest {
    @Test
    fun itResolvesSysPropSettingFirst() {
        val setting = strEnvSetting("FOO_BAR", "foo.bar").orElse("error")
        val testPlatform = mockPlatform(mapOf("FOO_BAR" to "error"), mapOf("foo.bar" to "success"))

        val actual = setting.resolve(testPlatform)
        assertEquals("success", actual)
    }

    @Test
    fun itResolvesEnvVarSettingSecond() {
        val setting = strEnvSetting("FOO_BAR", "foo.bar").orElse("error")
        val testPlatform = mockPlatform(mapOf("FOO_BAR" to "success"))

        val actual = setting.resolve(testPlatform)
        assertEquals("success", actual)
    }

    @Test
    fun itResolvesDefaultSettingThird() {
        val setting = strEnvSetting("FOO_BAR", "foo.bar").orElse("success")
        val testPlatform = mockPlatform()

        val actual = setting.resolve(testPlatform)
        assertEquals("success", actual)
    }

    @Test
    fun itReturnsNullWithNoValue() {
        val setting = strEnvSetting("FOO_BAR", "foo.bar")
        val testPlatform = mockPlatform()

        assertNull(setting.resolve(testPlatform))
    }

    @Test
    fun itResolvesBooleans() {
        val setting = boolEnvSetting("FOO_BAR", "foo.bar")
        val testPlatform = mockPlatform(mapOf("FOO_BAR" to "TRUE"))

        val actual = setting.resolve(testPlatform)
        assertEquals(true, actual)
    }

    @Test
    fun itResolvesIntegers() {
        val setting = intEnvSetting("FOO_BAR", "foo.bar")
        val testPlatform = mockPlatform(mapOf("FOO_BAR" to "42"))

        val actual = setting.resolve(testPlatform)
        assertEquals(42, actual)
    }

    @Test
    fun itResolvesLongs() {
        val setting = longEnvSetting("FOO_BAR", "foo.bar")
        val testPlatform = mockPlatform(mapOf("FOO_BAR" to "-4294967296"))

        val actual = setting.resolve(testPlatform)
        assertEquals(-4294967296L, actual)
    }

    @Test
    fun itResolvesEnums() {
        val setting = enumEnvSetting<Suit>("FOO_BAR", "foo.bar")
        val testPlatform = mockPlatform(mapOf("FOO_BAR" to "spades"))

        val actual = setting.resolve(testPlatform)
        assertEquals(Suit.Spades, actual)
    }
}

private fun mockPlatform(envVars: Map<String, String> = mapOf(), sysProps: Map<String, String> = mapOf()) =
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
