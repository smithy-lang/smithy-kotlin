/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net

import aws.smithy.kotlin.runtime.InternalApi

internal actual object DefaultHostResolver : HostResolver {
    actual override suspend fun resolve(hostname: String): List<HostAddress> {
        TODO("Not yet implemented")
    }

    actual override fun reportFailure(addr: HostAddress) {
        TODO("Not yet implemented")
    }

    @InternalApi
    actual override fun purgeCache(addr: HostAddress?) {
        TODO("Not yet implemented")
    }
}
