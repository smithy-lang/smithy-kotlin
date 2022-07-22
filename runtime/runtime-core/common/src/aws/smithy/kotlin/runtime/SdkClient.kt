/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime

import aws.smithy.kotlin.runtime.io.Closeable

/**
 * Common interface all generated service clients implement
 */
public interface SdkClient : Closeable {
    public val serviceName: String

    override fun close() {}
}
