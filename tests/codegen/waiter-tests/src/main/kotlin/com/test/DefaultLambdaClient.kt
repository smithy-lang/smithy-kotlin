package com.test

import com.test.model.GetFunctionRequest
import com.test.model.GetFunctionResponse
import kotlin.Result

/**
 * This is a stub that produces a series of responses. The marker is a series of dots "...", where
 * each dot represents a service round trip.  Within each service round trip a list of [FunctionConfiguration]
 * is generated including the round trip index.
 *
 * NOTE: Regarding potential SDK codegen conflicts with this type, in this test we do not supply
 * a protocol generator when generating the SDK.  This results in an abstract client (LambdaClient)
 * to be generated but not a concrete implementation.  We fill in the concrete client with our
 * test stub.  Due to codegen coupling between the abstract and concrete clients, the client must be
 * named 'Default<ClientName>`.
 */
class TestLambdaClient(resultList: List<Result<GetFunctionResponse>>) : LambdaClient {
    override val config: LambdaClient.Config
        get() = error("Unneeded for test")

    private val results = resultList.iterator()

    override suspend fun getFunction(input: GetFunctionRequest): GetFunctionResponse {
        val nextResult = results.next()
        return if (nextResult.isSuccess) nextResult.getOrNull()!! else throw nextResult.exceptionOrNull()!!
    }
}
