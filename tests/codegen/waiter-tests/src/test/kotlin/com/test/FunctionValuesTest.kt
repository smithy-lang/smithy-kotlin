package com.test

import aws.smithy.kotlin.runtime.retries.Outcome
import aws.smithy.kotlin.runtime.retries.getOrThrow
import com.test.model.EntityPrimitives
import com.test.model.GetFunctionValuesEqualsRequest
import com.test.model.GetFunctionValuesEqualsResponse
import com.test.waiters.waitUntilValuesFunctionPrimitivesEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FunctionValuesTest {
    private fun successTest(
        block: suspend WaitersTestClient.(request: GetFunctionValuesEqualsRequest) -> Outcome<GetFunctionValuesEqualsResponse>, // TODO: Make this generic
        vararg results: GetFunctionValuesEqualsResponse,
    ): Unit = runTest {
        val client = DefaultWaitersTestClient(results.map { Result.success(it) })
        val req = GetFunctionValuesEqualsRequest { name = "test" }

        val outcome = client.block(req)
        assertEquals(results.size, outcome.attempts)
        assertEquals(results.last(), outcome.getOrThrow())
    }

    @Test
    fun testValuesFunctionPrimitivesEquals() = successTest(
        WaitersTestClient::waitUntilValuesFunctionPrimitivesEquals,
        GetFunctionValuesEqualsResponse { primitives = EntityPrimitives { string = "foo" } },
    )
}