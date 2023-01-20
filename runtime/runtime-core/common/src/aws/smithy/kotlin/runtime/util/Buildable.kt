/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

/**
 * A type that knows how to build another type
 * @param T the type that is built by [build]
 */
public interface Buildable<out T> {
    /**
     * Create a new instance of type [T]
     */
    public fun build(): T
}
