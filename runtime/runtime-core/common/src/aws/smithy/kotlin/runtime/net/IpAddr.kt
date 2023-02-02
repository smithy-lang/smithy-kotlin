/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.net

import aws.smithy.kotlin.runtime.InternalApi

/**
 * An IP Address (either IPv4 or IPv6)
 */
@InternalApi
public sealed class IpAddr {
    public companion object {
        /**
         * Parse a string into an [IpAddr]. Fails if [s] is not a valid IP address
         */
        public fun parse(s: String): IpAddr = when {
            s.isIpv4() -> IpV4Addr.parse(s)
            else -> IpV6Addr.parse(s)
        }
    }

    /**
     * The raw numerical address
     */
    public abstract val octets: ByteArray

    /**
     * The formatted string representation of a numerical address
     */
    public abstract val address: String

    /**
     * Returns true if this is the loopback address
     */
    public abstract val isLoopBack: Boolean

    /**
     * Returns true if this is the "any" address (e.g. `0.0.0.0` or `::`)
     */
    public abstract val isUnspecified: Boolean
}
