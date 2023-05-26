/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package smithy.kotlin.traits

import PaginatorTest
import kotlinx.coroutines.flow.*
import smithy.kotlin.traits.paginators.functions
import smithy.kotlin.traits.paginators.listFunctionsPaginated

class StandardPaginatorTest : PaginatorTest() {
    override suspend fun getByPages(unit: LambdaClient): List<String> = unit
        .listFunctionsPaginated { }
        .transform { it.functions?.forEach { emit(it.functionName!!) } }
        .toList()
        .sorted()

    override suspend fun getByItems(unit: LambdaClient): List<String> = unit
        .listFunctionsPaginated { }
        .functions()
        .map { it.functionName!! }
        .toList()
        .sorted()
}
