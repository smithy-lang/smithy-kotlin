/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.InternalApi

/**
 * An implementation of [PlatformProvider] meant for testing
 * @param env Environment variable mappings
 * @param props System property mappings
 * @param fs Filesystem path to content mappings
 * @param os Operating system info to emulate
 */
@InternalApi
public class TestPlatformProvider(
    env: Map<String, String> = emptyMap(),
    private val props: Map<String, String> = emptyMap(),
    private val fs: Map<String, String> = emptyMap(),
    private val os: OperatingSystem = OperatingSystem(OsFamily.Linux, "test"),
) : PlatformProvider, Filesystem by Filesystem.fromMap(fs.mapValues { it.value.encodeToByteArray() }) {

    public companion object;

    // ensure HOME directory is set for path normalization. this is mostly for AWS config loader behavior
    private val env = if (env.containsKey("HOME")) env else env.toMutableMap().apply { put("HOME", "/users/test") }
    override val filePathSeparator: String
        get() = when (os.family) {
            OsFamily.Windows -> "\\"
            else -> "/"
        }

    override val isJvm: Boolean = true
    override val isAndroid: Boolean = false
    override val isBrowser: Boolean = false
    override val isNode: Boolean = false
    override val isNative: Boolean = false
    override fun osInfo(): OperatingSystem = os
    override fun getAllProperties(): Map<String, String> = props
    override fun getProperty(key: String): String? = props[key]
    override fun getAllEnvVars(): Map<String, String> = env
    override fun getenv(key: String): String? = env[key]
}
