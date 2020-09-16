/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt

/**
 * Common interface all generated service clients implement
 */
interface SdkClient {
    val serviceName: String

    fun close() {}
}
