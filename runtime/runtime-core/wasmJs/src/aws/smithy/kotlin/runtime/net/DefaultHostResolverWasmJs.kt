/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net

internal actual object DefaultHostResolver : HostResolver {
    actual override suspend fun resolve(hostname: String): List<HostAddress> = TODO("Not yet implemented on wasmJs: host resolution")

    // No-op, matches JVM and native implementations
    actual override fun purgeCache(addr: HostAddress?) { }

    // No-op, matches JVM and native implementations
    actual override fun reportFailure(addr: HostAddress) { }
}
