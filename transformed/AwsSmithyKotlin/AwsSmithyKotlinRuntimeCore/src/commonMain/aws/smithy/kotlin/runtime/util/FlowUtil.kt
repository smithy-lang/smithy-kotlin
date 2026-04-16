/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.InternalApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

@InternalApi
public fun <T> mergeSequential(vararg flows: Flow<T>): Flow<T> = flow {
    flows.forEach { flow -> emitAll(flow) }
}
