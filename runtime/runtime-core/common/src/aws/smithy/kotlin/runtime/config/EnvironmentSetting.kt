/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.config

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.util.PlatformEnvironProvider
import aws.smithy.kotlin.runtime.util.PlatformProvider

@InternalApi
public typealias EnvSettingFactory<T> = (String, String) -> EnvironmentSetting<T>

/**
 * Describes a setting whose value may be retrieved from the local environment, usually via a JVM system property or an
 * environment variable.
 * @param T The type of values for this property
 * @param parse A function that maps string values into the type of this setting
 * @param sysProp The name of the JVM system property where this setting may be configured
 * @param envVar The name of the environment variable where this setting may be configured
 * @param defaultValue The default value (if one exists)
 */
@InternalApi
public data class EnvironmentSetting<T>(
    public val parse: (String) -> T,
    public val sysProp: String,
    public val envVar: String,
    public val defaultValue: T? = null,
) {
    @InternalApi
    public companion object {
        public operator fun <T> invoke(asTyped: (String) -> T): EnvSettingFactory<T> =
            { sysProp: String, envVar: String -> EnvironmentSetting(asTyped, sysProp, envVar) }
    }

    public fun orElse(defaultValue: T): EnvironmentSetting<T> = copy(defaultValue = defaultValue)
}

/**
 * Resolves an environment setting from the environment. This method attempts to resolve the setting via the system
 * property first, falling back to the environment variable if necessary. If neither is set, it returns the setting's
 * default value.
 * @param platform The [PlatformEnvironProvider] to use for reading system properties and environment variables.
 * Defaults to [PlatformProvider.System].
 */
@InternalApi
public fun <T> EnvironmentSetting<T>.resolve(platform: PlatformEnvironProvider = PlatformProvider.System): T? {
    val stringValue = platform.getProperty(sysProp) ?: platform.getenv(envVar)
    return stringValue?.let(parse) ?: defaultValue
}

/* ktlint-disable spacing-between-declarations-with-annotations */
@InternalApi public val boolEnvSetting: EnvSettingFactory<Boolean> = EnvironmentSetting(String::toBoolean)
@InternalApi public val intEnvSetting: EnvSettingFactory<Int> = EnvironmentSetting(String::toInt)
@InternalApi public val longEnvSetting: EnvSettingFactory<Long> = EnvironmentSetting(String::toLong)
@InternalApi public val strEnvSetting: EnvSettingFactory<String> = EnvironmentSetting { it }
/* ktlint-enable spacing-between-declarations-with-annotations */

@InternalApi
public inline fun <reified T : Enum<T>> enumEnvSetting(sysProp: String, envVar: String): EnvironmentSetting<T> {
    val parse = { strValue: String ->
        val allValues = enumValues<T>()
        allValues
            .firstOrNull { it.name.equals(strValue, ignoreCase = true) }
            ?: throw ClientException("Value $strValue is not supported, should be one of ${allValues.joinToString(", ")}")
    }
    return EnvironmentSetting(parse, sysProp, envVar)
}
