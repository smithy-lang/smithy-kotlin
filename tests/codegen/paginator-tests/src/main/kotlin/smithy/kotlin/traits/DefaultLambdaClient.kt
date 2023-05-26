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
        nextMarker = when {
            /* ktlint-disable no-multi-spaces */
            input.marker.toIntOrZero() == pageCount - 1 -> exhaustedVal // Exhausted pages
            input.marker == null -> "1"                                 // First page
            else -> (input.marker.toInt() + 1).toString()               // Next page
            /* ktlint-enable no-multi-spaces */
        }

        functions = generateFunctions(input.marker.toIntOrZero())
    }

    override suspend fun truncatedListFunctions(input: TruncatedListFunctionsRequest) = TruncatedListFunctionsResponse {
        nextMarker = (input.marker.toIntOrZero() + 1).toString()
        isTruncated = input.marker.toIntOrZero() < pageCount - 1
        functions = generateFunctions(input.marker.toIntOrZero())
    }

    private fun generateFunctions(page: Int) = (0 until itemsPerPage).map { idx ->
        FunctionConfiguration { functionName = "Function page($page) item($idx)" }
    }
}

private fun String?.toIntOrZero() = when (this) {
    null -> 0
    else -> toInt()
}
