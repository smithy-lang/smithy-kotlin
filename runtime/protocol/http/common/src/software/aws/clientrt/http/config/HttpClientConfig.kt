/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http.config

import software.aws.clientrt.http.engine.HttpClientEngine

/**
 * The user-accessible configuration properties for the SDKs internal HTTP client facility.
 */
interface HttpClientConfig {
    /**
     * Allows for overriding the default HTTP client engine.
     */
    val httpClientEngine: HttpClientEngine?
}
