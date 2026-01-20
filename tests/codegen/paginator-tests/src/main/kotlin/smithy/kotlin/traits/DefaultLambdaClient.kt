/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package smithy.kotlin.traits

import smithy.kotlin.traits.model.*

/**
 * This is a stub that produces pages of responses. The marker is a string number (e.g., "1", "2", etc.) indicating the
 * page number. Within each service round trip a list of [FunctionConfiguration] is generated including the round trip
 * index.
 *
 * NOTE: Regarding potential SDK codegen conflicts with this type, in this test we do not supply
 * a protocol generator when generating the SDK.  This results in an abstract client (LambdaClient)
 * to be generated but not a concrete implementation.  We fill in the concrete client with our
 * test stub.  Due to codegen coupling between the abstract and concrete clients, the client must be
 * named `Default<ClientName>`.
 */
class TestLambdaClient : LambdaClient {
    override val config: LambdaClient.Config
        get() = error("Unneeded for test")

    override fun close() { }

    // Number of pages to generate
    var pageCount: Int = 10

    // Number of items to generate per page
    var itemsPerPage: Int = 3

    // Value quantifying exhaustion
    var exhaustedVal: String? = null

    override suspend fun listFunctions(input: ListFunctionsRequest) = ListFunctionsResponse {
        val inputMarker = input.marker.toIntOrZero()

        nextMarker = when (inputMarker) {
            pageCount - 1 -> exhaustedVal // Exhausted pages
            else -> (inputMarker + 1).toString() // Next page
        }

        functions = generateFunctions(inputMarker)
    }

    override suspend fun truncatedListFunctions(input: TruncatedListFunctionsRequest) = TruncatedListFunctionsResponse {
        val inputMarker = input.marker.toIntOrZero()

        nextMarker = (inputMarker + 1).toString()
        isTruncated = inputMarker < pageCount - 1
        functions = generateFunctions(inputMarker)
    }

    override suspend fun identicalTokenListFunctions(input: IdenticalTokenListFunctionsRequest) = IdenticalTokenListFunctionsResponse {
        val inputMarker = input.marker.toIntOrZero()

        nextMarker = when (inputMarker) {
            pageCount - 1 -> input.marker // Exhausted pages, return identical input marker
            else -> (inputMarker + 1).toString() // Next page
        }

        functions = generateFunctions(inputMarker)
    }

    private fun generateFunctions(page: Int): List<FunctionConfiguration> {
        require(page < pageCount) { "Paginator tried to seek beyond max page $pageCount" }
        return (0 until itemsPerPage).map { idx ->
            FunctionConfiguration { functionName = "Function page($page) item($idx)" }
        }
    }
}

private fun String?.toIntOrZero() = when (this) {
    null -> 0
    else -> toInt()
}
