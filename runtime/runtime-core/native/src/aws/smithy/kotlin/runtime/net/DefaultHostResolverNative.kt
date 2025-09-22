/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net

import aws.sdk.kotlin.crt.io.CrtHostAddress
import aws.sdk.kotlin.crt.use
import aws.sdk.kotlin.crt.io.HostResolver as CrtHostResolver

internal actual object DefaultHostResolver : HostResolver {
    actual override suspend fun resolve(hostname: String): List<HostAddress> {
        val results = CrtHostResolver().use {
            it.resolve(hostname)
        }
        return results.map { it.toHostAddress() }
    }

    // No-op, matches JVM implementation
    actual override fun purgeCache(addr: HostAddress?) { }

    // No-op, matches JVM implementation
    actual override fun reportFailure(addr: HostAddress) { }

    private fun CrtHostAddress.toHostAddress() = HostAddress(host, IpAddr.parse(address))
}