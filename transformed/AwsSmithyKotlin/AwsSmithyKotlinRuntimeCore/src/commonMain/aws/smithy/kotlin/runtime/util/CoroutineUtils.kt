/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.InternalApi
import kotlinx.coroutines.CoroutineName
import kotlin.coroutines.CoroutineContext

/**
 * Append to the existing coroutine name if it exists in the context otherwise
 * use [name] as is.
 * @return the [CoroutineName] context element
 */
@InternalApi
public fun CoroutineContext.derivedName(name: String): CoroutineName {
    val existing = get(CoroutineName)?.name ?: return CoroutineName(name)
    return CoroutineName("$existing:$name")
}
