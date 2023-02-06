/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine

import aws.smithy.kotlin.runtime.net.Url

/**
 * Selects the proxy to use for a given [Url]. Implementations **MUST** be stable and return the
 * same [ProxyConfig] for a given [Url].
 */
public fun interface ProxySelector {
    /**
     * Return the proxy configuration to use for [Url]
     */
    public fun select(url: Url): ProxyConfig

    public companion object {
        /**
         * Explicitly disable proxy selection
         */
        public val NoProxy: ProxySelector = ProxySelector { ProxyConfig.Direct }
    }
}
