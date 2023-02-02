/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.InternalApi

/**
 * A collection of opaque resources that implement [SdkManaged].
 */
@InternalApi
public class SdkManagedGroup(
    private val resources: MutableList<SdkManaged> = mutableListOf(),
) {
    public fun add(resource: SdkManaged) {
        resource.share()
        resources.add(resource)
    }

    public fun unshareAll() {
        resources.forEach { it.unshare() }
    }
}

/**
 * Delegate extension to add a resource to the group if applicable.
 */
@InternalApi
public fun SdkManagedGroup.addIfManaged(resource: Any) {
    if (resource is SdkManaged) add(resource)
}
