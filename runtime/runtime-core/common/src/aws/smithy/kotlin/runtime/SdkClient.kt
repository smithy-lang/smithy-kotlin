/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime

/**
 * Common interface all generated service clients implement
 */
interface SdkClient {
    val serviceName: String

    fun close() {}
}
