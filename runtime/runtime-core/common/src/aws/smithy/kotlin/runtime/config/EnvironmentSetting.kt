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

@InternalApi public val boolEnvSetting: EnvSettingFactory<Boolean> = EnvironmentSetting(String::toBoolean)

@InternalApi public val intEnvSetting: EnvSettingFactory<Int> = EnvironmentSetting(String::toInt)

@InternalApi public val longEnvSetting: EnvSettingFactory<Long> = EnvironmentSetting(String::toLong)

@InternalApi public val strEnvSetting: EnvSettingFactory<String> = EnvironmentSetting { it }

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

@InternalApi
public fun <T> EnvironmentSetting<T>.withCaseInsensitiveSuffixes(
    sysPropSuffix: String,
    envVarSuffix: String,
): CaseInsensitiveEnvironmentSetting<T> =
    CaseInsensitiveEnvironmentSetting(parse, sysProp, sysPropSuffix, envVar, envVarSuffix, defaultValue)

@InternalApi
public data class CaseInsensitiveEnvironmentSetting<T>(
    public val parse: (String) -> T,
    public val sysPropBase: String,
    public val sysPropSuffix: String,
    public val envVarBase: String,
    public val envVarSuffix: String,
    public val defaultValue: T? = null,
) {
    internal val sysPropRegex = caseInsensitiveRegex(sysPropBase, sysPropSuffix)
    internal val envVarRegex = caseInsensitiveRegex(envVarBase, envVarSuffix)
}

private fun caseInsensitiveRegex(base: String, caseInsensitiveSuffix: String) =
    buildString {
        append('^') // beginning of string
        append(Regex.escape(base)) // base component, escaped
        append("(?i)") // turn on case-insensitivity
        append(Regex.escape(caseInsensitiveSuffix)) // case-insensitive suffix, escaped
        append('$') // end of string
    }.toRegex()

/**
 * Resolves an environment setting from the environment. This method attempts to resolve the setting via the system
 * property first, falling back to the environment variable if necessary. If neither is set, it returns the setting's
 * default value.
 * @param platform The [PlatformEnvironProvider] to use for reading system properties and environment variables.
 * Defaults to [PlatformProvider.System].
 */
@InternalApi
public fun <T> CaseInsensitiveEnvironmentSetting<T>.resolve(
    platform: PlatformEnvironProvider = PlatformProvider.System,
): T? {
    val stringValue = resolveStringValue(platform.getAllProperties(), sysPropRegex)
        ?: resolveStringValue(platform.getAllEnvVars(), envVarRegex)
    return stringValue?.let(parse) ?: defaultValue
}

private fun resolveStringValue(sourceValues: Map<String, String>, keyRegex: Regex): String? =
    sourceValues.entries.firstNotNullOfOrNull { (key, value) -> value.takeIf { keyRegex.matches(key) } }
