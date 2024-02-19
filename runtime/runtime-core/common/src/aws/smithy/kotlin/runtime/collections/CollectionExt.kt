/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections

/**
 * Creates a new list or appends to an existing one if not null.
 *
 * If [dest] is null this function creates a new list with element [x] and returns it.
 * Otherwise, it appends [x] to [dest] and returns the given [dest] list.
 */
public fun <T> createOrAppend(dest: MutableList<T>?, x: T): MutableList<T> {
    if (dest == null) return mutableListOf(x)
    dest.add(x)
    return dest
}
