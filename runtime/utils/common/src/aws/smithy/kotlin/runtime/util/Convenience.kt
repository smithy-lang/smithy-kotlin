/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.util

/**
 * Determines the length of a collection. This is a synonym for [Collection.size].
 */
val <T> Collection<T>.length: Int
    get() = size
