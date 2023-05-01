/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import com.test.TestLambdaClient
import com.test.model.ListFunctionsRequest
import com.test.paginators.functions
import com.test.paginators.listFunctionsPaginated
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class PaginatorTest {

    @Test
    fun canPaginateMultiplePagesSingleItem() = runBlocking {
        val unit = TestLambdaClient()

        unit.pageCount = 5
        unit.itemsPerPage = 1
        val fnNames = mutableListOf<String>()

        unit
            .listFunctionsPaginated(ListFunctionsRequest {})
            .collect { resp ->
                fnNames.addAll(resp.functions?.mapNotNull { it.functionName } ?: emptyList())
            }

        assertEquals(unit.pageCount * unit.itemsPerPage, fnNames.size)
        assertEquals(
            "[Function page(0) item(0), Function page(1) item(0), Function page(2) item(0), Function page(3) item(0), Function page(4) item(0)]",
            run { fnNames.sort(); fnNames }.toString(),
        )
    }

    @Test
    fun canPaginateMultiplePagesSingleItemOnItems() = runBlocking {
        val unit = TestLambdaClient()

        unit.pageCount = 5
        unit.itemsPerPage = 1
        val fnNames = mutableListOf<String>()

        unit
            .listFunctionsPaginated(ListFunctionsRequest {})
            .functions()
            .collect { functionConfiguration ->
                functionConfiguration.functionName?.let { it -> fnNames.add(it) }
            }

        assertEquals(unit.pageCount * unit.itemsPerPage, fnNames.size)
        assertEquals(
            "[Function page(0) item(0), Function page(1) item(0), Function page(2) item(0), Function page(3) item(0), Function page(4) item(0)]",
            run { fnNames.sort(); fnNames }.toString(),
        )
    }

    @Test
    fun canPaginateSingePage() = runBlocking {
        val unit = TestLambdaClient()

        unit.pageCount = 1
        unit.itemsPerPage = 10
        val fnNames = mutableListOf<String>()
        unit.listFunctionsPaginated(ListFunctionsRequest {}).collect { resp ->
            fnNames.addAll(resp.functions?.mapNotNull { it.functionName } ?: emptyList())
        }

        assertEquals(unit.pageCount * unit.itemsPerPage, fnNames.size)
        assertEquals(
            "[Function page(0) item(0), Function page(0) item(1), Function page(0) item(2), Function page(0) item(3), Function page(0) item(4), Function page(0) item(5), Function page(0) item(6), Function page(0) item(7), Function page(0) item(8), Function page(0) item(9)]",
            run { fnNames.sort(); fnNames }.toString(),
        )
    }

    @Test
    fun canPaginateSingePageOnItem() = runBlocking {
        val unit = TestLambdaClient()

        unit.pageCount = 1
        unit.itemsPerPage = 10
        val fnNames = mutableListOf<String>()

        unit
            .listFunctionsPaginated(ListFunctionsRequest {})
            .functions()
            .collect { functionConfiguration ->
                functionConfiguration.functionName?.let { name -> fnNames.add(name) }
            }

        assertEquals(unit.pageCount * unit.itemsPerPage, fnNames.size)
        assertEquals(
            "[Function page(0) item(0), Function page(0) item(1), Function page(0) item(2), Function page(0) item(3), Function page(0) item(4), Function page(0) item(5), Function page(0) item(6), Function page(0) item(7), Function page(0) item(8), Function page(0) item(9)]",
            run { fnNames.sort(); fnNames }.toString(),
        )
    }

    @Test
    fun canPaginateSinglePageNoItems() = runBlocking {
        val unit = TestLambdaClient()

        unit.pageCount = 1
        unit.itemsPerPage = 0
        val fnNames = mutableListOf<String>()

        unit
            .listFunctionsPaginated(ListFunctionsRequest {})
            .collect { resp ->
                fnNames.addAll(resp.functions?.mapNotNull { it.functionName } ?: emptyList())
            }

        assertEquals(unit.pageCount * unit.itemsPerPage, fnNames.size)
        assertEquals("[]", fnNames.toString())
    }

    @Test
    fun canPaginateSinglePageNoItemsOnItem() = runBlocking {
        val unit = TestLambdaClient()

        unit.pageCount = 1
        unit.itemsPerPage = 0
        val fnNames = mutableListOf<String>()

        unit
            .listFunctionsPaginated(ListFunctionsRequest {})
            .functions()
            .collect { functionConfiguration ->
                functionConfiguration.functionName?.let { name -> fnNames.add(name) }
            }

        assertEquals(unit.pageCount * unit.itemsPerPage, fnNames.size)
        assertEquals("[]", fnNames.toString())
    }

    @Test
    fun canPaginateMultiplePagesMultipleItems() = runBlocking {
        val unit = TestLambdaClient()

        unit.pageCount = 2
        unit.itemsPerPage = 2
        val fnNames = mutableListOf<String>()

        unit
            .listFunctionsPaginated(ListFunctionsRequest {})
            .collect { resp ->
                fnNames.addAll(resp.functions?.mapNotNull { it.functionName } ?: emptyList())
            }

        assertEquals(unit.pageCount * unit.itemsPerPage, fnNames.size)
        assertEquals(
            "[Function page(0) item(0), Function page(0) item(1), Function page(1) item(0), Function page(1) item(1)]",
            run { fnNames.sort(); fnNames }.toString(),
        )
    }

    @Test
    fun canPaginateMultiplePagesMultipleItemsOnItems() = runBlocking {
        val unit = TestLambdaClient()

        unit.pageCount = 2
        unit.itemsPerPage = 2
        val fnNames = mutableListOf<String>()
        unit
            .listFunctionsPaginated(ListFunctionsRequest {})
            .functions()
            .collect { functionConfiguration ->
                functionConfiguration.functionName?.let { name -> fnNames.add(name) }
            }

        assertEquals(unit.pageCount * unit.itemsPerPage, fnNames.size)
        assertEquals(
            "[Function page(0) item(0), Function page(0) item(1), Function page(1) item(0), Function page(1) item(1)]",
            run { fnNames.sort(); fnNames }.toString(),
        )
    }

    @Test
    fun handleServiceReturningEmptyStringForTerminus() = runBlocking {
        val unit = TestLambdaClient()

        unit.pageCount = 2
        unit.itemsPerPage = 2
        unit.exhaustedVal = ""
        val fnNames = mutableListOf<String>()
        unit
            .listFunctionsPaginated(ListFunctionsRequest {})
            .functions()
            .collect { functionConfiguration ->
                functionConfiguration.functionName?.let { name -> fnNames.add(name) }
            }

        assertEquals(unit.pageCount * unit.itemsPerPage, fnNames.size)
        assertEquals(
            "[Function page(0) item(0), Function page(0) item(1), Function page(1) item(0), Function page(1) item(1)]",
            run { fnNames.sort(); fnNames }.toString(),
        )
    }
}
