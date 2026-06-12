/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.InternalApi
import kotlin.jvm.JvmName

/**
 * An implementation of [PlatformProvider] meant for testing
 * @param env Environment variable mappings
 * @param props System property mappings
 * @param fs Filesystem path to content mappings
 * @param os Operating system info to emulate
 */
@InternalApi
public class TestPlatformProvider
private constructor(
    env: Map<String, String> = emptyMap(),
    private val props: Map<String, String> = emptyMap(),
    public val fs: Filesystem = MapFilesystem(),
    private val os: OperatingSystem = OperatingSystem(OsFamily.Linux, "test"),
) : PlatformProvider,
    Filesystem by fs {

    // TODO deprecate
    public constructor() : this(emptyMap(), emptyMap(), MapFilesystem(), OperatingSystem(OsFamily.Linux, "test"))

    // TODO deprecate
    public constructor(
        env: Map<String, String> = emptyMap(),
        props: Map<String, String> = emptyMap(),
        fs: Map<String, String> = emptyMap(),
        os: OperatingSystem = OperatingSystem(OsFamily.Linux, "test"),
    ) : this(env, props, MapFilesystem(TestFile.transformMap(fs).toMutableMap()), os)

    @InternalApi
    public companion object {
        public fun of(
            env: Map<String, String> = emptyMap(),
            props: Map<String, String> = emptyMap(),
            fs: Map<String, TestFile> = emptyMap(),
            os: OperatingSystem = OperatingSystem(OsFamily.Linux, "test"),
        ): TestPlatformProvider = TestPlatformProvider(env, props, MapFilesystem(fs.toMutableMap()), os)
    }

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

@InternalApi
public class TestFile(public val contents: ByteArray, public val permissions: String? = null) {
    @InternalApi
    public companion object {
        @JvmName("transformMapStringByteArray")
        public fun transformMap(
            pathsToContents: Map<String, ByteArray>,
        ): Map<String, TestFile> = pathsToContents.mapValues { (_, contents) -> TestFile(contents) }

        @JvmName("transformMapStringString")
        public fun transformMap(
            pathsToContents: Map<String, String>,
        ): Map<String, TestFile> = pathsToContents.mapValues { (_, contents) -> TestFile(contents) }
    }

    public constructor(contents: String, permissions: String? = null) : this(contents.encodeToByteArray(), permissions)
}
