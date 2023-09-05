package com.test

import aws.smithy.kotlin.runtime.retries.Outcome
import aws.smithy.kotlin.runtime.retries.getOrThrow
import com.test.model.EntityPrimitives
import com.test.model.GetFunctionKeysEqualsRequest
import com.test.model.GetFunctionKeysEqualsResponse
import com.test.model.TwoDimensionalEntityLists
import com.test.waiters.waitUntilKeysFunctionPrimitivesIntegerEquals
import com.test.waiters.waitUntilKeysFunctionPrimitivesStringEquals
import com.test.waiters.waitUntilKeysFunctionTwoDimensionalListsEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FunctionKeysTest {
    private fun successTest(
        block: suspend WaitersTestClient.(request: GetFunctionKeysEqualsRequest) -> Outcome<GetFunctionKeysEqualsResponse>,
        vararg results: GetFunctionKeysEqualsResponse,
    ): Unit = runTest {
        val client = DefaultWaitersTestClient(results.map { Result.success(it) })
        val req = GetFunctionKeysEqualsRequest { name = "test" }

        val outcome = client.block(req)
        assertEquals(results.size, outcome.attempts)
        assertEquals(results.last(), outcome.getOrThrow())
    }

    @Test
    fun testKeysFunctionPrimitivesStringEquals() = successTest(
        WaitersTestClient::waitUntilKeysFunctionPrimitivesStringEquals,
        GetFunctionKeysEqualsResponse { primitives = EntityPrimitives {  } },
    )

    @Test
    fun testKeysFunctionPrimitivesIntegerEquals() = successTest(
        WaitersTestClient::waitUntilKeysFunctionPrimitivesIntegerEquals,
        GetFunctionKeysEqualsResponse { primitives = EntityPrimitives {  } },
    )

    @Test
    fun testKeysFunctionTwoDimensionalListsEquals() = successTest(
        WaitersTestClient::waitUntilKeysFunctionTwoDimensionalListsEquals,
        GetFunctionKeysEqualsResponse { twoDimensionalLists = TwoDimensionalEntityLists {  } },
    )
}