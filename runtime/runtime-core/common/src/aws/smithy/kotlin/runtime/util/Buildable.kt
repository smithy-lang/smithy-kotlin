/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

/**
 * A type that knows how to build another type
 */
public interface Buildable<out T> {
    public fun build(): T
}
