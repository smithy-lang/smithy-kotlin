/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.engine

import aws.smithy.kotlin.runtime.http.Url

/**
 * Selects the proxy to use for a given [Url]
 */
fun interface ProxySelector {
    /**
     * Return the proxy configuration to use for [Url]
     */
    fun select(url: Url): ProxyConfig
}
