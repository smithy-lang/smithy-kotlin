/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * Wraps a [Closeable] to operate as an [SdkManaged] with share count tracking.
 * The final [unshare] call will trigger the closing of the resource.
 */
@InternalApi
public open class SdkManagedCloseable(private val closeable: Closeable) : SdkManagedBase() {
    override fun unshare(): Boolean {
        val shouldClose = super.unshare()
        if (shouldClose) closeable.close()
        return shouldClose
    }
}
