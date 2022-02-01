/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

import aws.smithy.kotlin.runtime.ServiceErrorMetadata
import aws.smithy.kotlin.runtime.ServiceException
import aws.smithy.kotlin.runtime.retries.getOrThrow
import aws.smithy.kotlin.runtime.util.InternalApi
import com.test.TestLambdaClient
import com.test.model.GetFunctionResponse
import com.test.waiters.waitUntilFunctionExistsBySuccess
import com.test.waiters.waitUntilFunctionHasNameTagByOutput
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class WaiterTest {
    @OptIn(InternalApi::class)
    private fun failure(errorCode: String) = Result.failure<GetFunctionResponse>(
        ServiceException().apply {
            sdkErrorMetadata.attributes[ServiceErrorMetadata.ErrorCode] = errorCode
        }
    )

    private fun success(builderBlock: GetFunctionResponse.Builder.() -> Unit) =
        GetFunctionResponse.Builder().apply(builderBlock).build().let { success(it) }

    private fun success(response: GetFunctionResponse) = Result.success(response)

    @Test
    fun testSuccessResponse(): Unit = runBlocking {
        val response = GetFunctionResponse { name = "Foo" }
        val results = listOf(
            failure("NotFound"),
            success(response),
        )
        val client = TestLambdaClient(results)

        val outcome = client.waitUntilFunctionExistsBySuccess { name = "Foo" }
        assertEquals(2, outcome.attempts)
        assertEquals(response, outcome.getOrThrow())
    }

    @Test
    fun testErrorResponse(): Unit = runBlocking {
        val results = listOf(
            failure("NotFound"),
            failure("NotFound"),
            failure("Unrecoverable"),
        )
        val client = TestLambdaClient(results)

        try {
            client.waitUntilFunctionExistsBySuccess { name = "Foo" }
            fail("Expected exception from waiter")
        } catch (e: ServiceException) {
            assertEquals("Unrecoverable", e.sdkErrorMetadata.errorCode)
        } catch (e: Throwable) {
            fail("Unexpected exception from waiter: $e")
        }
    }

    @Test
    fun testSuccessOutput(): Unit = runBlocking {
        val tagsResponse = GetFunctionResponse { name = "foo"; tags = mapOf("key" to "foo") }
        val results = listOf(
            success { name = "foo"                               }, // No tags
            success {               tags = mapOf("key" to "bar") }, // No name
            success { name = "foo"; tags = mapOf("key" to "bar") }, // Missing "foo" tag
            success(tagsResponse),                                  // Oll korrect
        )
        val client = TestLambdaClient(results)

        val outcome = client.waitUntilFunctionHasNameTagByOutput { name = "foo" }
        assertEquals(4, outcome.attempts)
        assertEquals(tagsResponse, outcome.getOrThrow())
    }
}
