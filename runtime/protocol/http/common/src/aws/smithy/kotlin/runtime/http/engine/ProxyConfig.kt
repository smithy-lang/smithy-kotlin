/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.engine

import aws.smithy.kotlin.runtime.http.Url

/**
 * A proxy configuration
 */
sealed class ProxyConfig {
    // TODO - TLS / auth config for proxy
    // FIXME - support env/system properties by default

    /**
     * HTTP based proxy
     */
    data class Http(val url: Url) : ProxyConfig() {
        constructor(url: String) : this(Url.parse(url))
    }
}
