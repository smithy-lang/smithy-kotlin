package aws.smithy.kotlin.runtime.config

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.util.PlatformEnvironProvider

@InternalApi
public typealias EnvSettingFactory<T> = (String, String) -> EnvironmentSetting<T>

/**
 * Describes a setting whose value may be retrieved from the local environment, usually via an environment variable or
 * a JVM system property.
 * @param T The type of values for this property
 * @param parse A function that maps string values into the type of this setting
 * @param envVar The name of the environment variable where this setting may be configured
 * @param sysProp The name of the JVM system property where this setting may be configured
 * @param defaultValue The default value (if one exists)
 */
@InternalApi
public data class EnvironmentSetting<T>(
    public val parse: (String) -> T,
    public val envVar: String,
    public val sysProp: String,
    public val defaultValue: T? = null,
) {
    @InternalApi
    public companion object {
        public operator fun <T> invoke(asTyped: (String) -> T): EnvSettingFactory<T> =
            { envVar: String, sysProp: String -> EnvironmentSetting(asTyped, envVar, sysProp) }
    }

    public fun orElse(defaultValue: T): EnvironmentSetting<T> = copy(defaultValue = defaultValue)
}

/**
 * Resolves an environment setting from the environment. This method attempts to resolve the setting via the system
 * property first, falling back to the environment variable if necessary. If neither is set, it returns the setting's
 * default value.
 * @param platform The [PlatformEnvironProvider] to use for reading system properties and environment variables.
 */
@InternalApi
public fun <T> EnvironmentSetting<T>.resolve(platform: PlatformEnvironProvider): T? {
    val stringValue = platform.getProperty(sysProp) ?: platform.getenv(envVar)
    return stringValue?.let(parse) ?: defaultValue
}

@InternalApi
public val boolEnvSetting: EnvSettingFactory<Boolean> = EnvironmentSetting(String::toBoolean)
@InternalApi
public val intEnvSetting: EnvSettingFactory<Int> = EnvironmentSetting(String::toInt)
@InternalApi
public val longEnvSetting: EnvSettingFactory<Long> = EnvironmentSetting(String::toLong)
@InternalApi
public val strEnvSetting: EnvSettingFactory<String> = EnvironmentSetting { it }

@InternalApi
public inline fun <reified T : Enum<T>> enumEnvSetting(envVar: String, sysProp: String): EnvironmentSetting<T> {
    val parse = { strValue: String ->
        val allValues = enumValues<T>()
        allValues
            .firstOrNull { it.name.equals(strValue, ignoreCase = true) }
            ?: throw ClientException("Value $strValue is not supported, should be one of ${allValues.joinToString(", ")}")
    }
    return EnvironmentSetting(parse, envVar, sysProp)
}
