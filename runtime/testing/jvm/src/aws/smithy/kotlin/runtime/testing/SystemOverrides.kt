/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.testing

import java.util.*

@PublishedApi
internal val editableEnvVars: MutableMap<String, String> by lazy {
    val systemEnv = System.getenv()
    val classOfMap = systemEnv::class.java

    @Suppress("UNCHECKED_CAST")
    classOfMap
        .getDeclaredField("m")
        .apply { isAccessible = true }
        .get(systemEnv) as MutableMap<String, String>
}

@PublishedApi
internal fun <K, V> MutableMap<K, V>.setAll(newEntries: Map<K, V>) {
    clear()
    putAll(newEntries)
}

/**
 * Runs the given [block] with modified environment variables. The variables given in [newVars] are merged onto the
 * existing environment variables, adding values that did not exist previously and updating values which did exist.
 * After the block completes, environment variables will be restored to their previous value. This method may be nested
 * multiple times to apply/restore hierarchical overrides.
 *
 * **Caution**: This method is not thread-safe as environment variables are global for a JVM instance.
 *
 * # How it works
 *
 * Normally environment variables cannot be modified within a JVM instance so this function makes use of private
 * reflection to gain access to the underlying mutable map used by [System.getenv]. At runtime, this requires
 * `--add-opens=java.base/java.util=ALL-UNNAMED` to be passed to the `java` executable. For instance:
 *
 * ```sh
 * java \
 *   -jar my-testing-jar \
 *   -classpath testing-jvm.X.Y.Z.jar,... \
 *   --add-opens=java.base/java.util=ALL-UNNAMED
 * ```
 *
 * In **smithy-kotlin** and **aws-sdk-kotlin** this is handled in the root scripts by adding a JVM arg to all JVM test
 * JARs (whether they rely on this module or not).
 *
 * @param newVars
 */
public suspend inline fun <T> withEnvVars(
    newVars: Map<String, String>,
    crossinline block: suspend () -> T,
): T {
    val originalVars = System.getenv()
    editableEnvVars.setAll(originalVars + newVars)
    return try {
        block()
    } finally {
        editableEnvVars.setAll(originalVars)
    }
}

public suspend inline fun <T> withSystemProperties(
    newProps: Map<String, String>,
    crossinline block: suspend () -> T,
): T {
    val originalProps = System.getProperties()
    val replacementProps = Properties().apply {
        putAll(originalProps)
        putAll(newProps)
    }
    System.setProperties(replacementProps)
    return try {
        block()
    } finally {
        System.setProperties(originalProps)
    }
}
