/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.util

/**
 * Indicates that an object supports a [deepCopy] operation which will return a copy that can be safely mutated without
 * affecting other instances.
 */
interface CanDeepCopy<out T> {
    /**
     * Returns a deep copy of this object.
     */
    fun deepCopy(): T
}
