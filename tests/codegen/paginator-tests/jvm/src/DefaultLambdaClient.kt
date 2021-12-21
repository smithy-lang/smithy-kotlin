package com.test

import com.test.model.FunctionConfiguration
import com.test.model.ListFunctionsRequest
import com.test.model.ListFunctionsResponse

/**
 * This is a stub that produces pages of responses.  The marker is a series of dots "...", where
 * each dot represents a service round trip.  Within each service round trip a list of [FunctionConfiguration]
 * is generated including the round trip index.
 */
class DefaultLambdaClient(config: LambdaClient.Config) : LambdaClient {
    override val config: LambdaClient.Config
        get() = TODO("Unneeded for test")

    // Number of pages to generate
    var pageCount: Int = 10
    // Number of items to generate per page
    var itemsPerPage: Int = 3

    override suspend fun listFunctions(input: ListFunctionsRequest): ListFunctionsResponse {
        return ListFunctionsResponse.invoke {
            nextMarker = when {
                (input.marker?.length ?: 0) == (pageCount - 1) -> null     // Exhausted pages
                input.marker == null -> "."                                // First page
                else -> "${input.marker}."                                 // Next page adds a dot to the marker
            }

            val index = input.marker?.length ?: 0

            val generatedFunctions = mutableListOf<FunctionConfiguration>()
            repeat(itemsPerPage) { index2 ->
                generatedFunctions.add(FunctionConfiguration {
                    functionName = "Function page($index) item($index2)"
                })
            }

            functions = generatedFunctions
        }
    }
}