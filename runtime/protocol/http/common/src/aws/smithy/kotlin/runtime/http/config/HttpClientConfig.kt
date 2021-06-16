/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.config

import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine

/**
 * The user-accessible configuration properties for the SDKs internal HTTP client facility.
 */
interface HttpClientConfig {
    /**
     * Allows for overriding the default HTTP client engine.
     */
    val httpClientEngine: HttpClientEngine?
}
