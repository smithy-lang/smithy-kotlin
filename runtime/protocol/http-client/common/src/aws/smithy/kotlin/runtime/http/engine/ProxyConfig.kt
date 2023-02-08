/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine

import aws.smithy.kotlin.runtime.net.Url

/**
 * A proxy configuration
 */
public sealed class ProxyConfig {
    /**
     * Represents a direct connection or absence of a proxy. Can be used to disable proxy support inferred from
     * environment for example.
     */
    public object Direct : ProxyConfig()

    /**
     * HTTP based proxy (with or without user/password auth)
     */
    public data class Http(public val url: Url) : ProxyConfig() {
        public constructor(url: String) : this(Url.parse(url))
    }
}
