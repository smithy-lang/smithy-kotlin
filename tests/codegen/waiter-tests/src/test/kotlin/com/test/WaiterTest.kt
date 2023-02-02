/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.test

import aws.smithy.kotlin.runtime.ServiceErrorMetadata
import aws.smithy.kotlin.runtime.ServiceException
import aws.smithy.kotlin.runtime.retries.getOrThrow
import aws.smithy.kotlin.runtime.InternalApi
import com.test.model.GetEntityResponse
import com.test.waiters.waitUntilEntityExistsBySuccess
import com.test.waiters.waitUntilEntityHasComparableNumericalValues
import com.test.waiters.waitUntilEntityHasNameTagByOutput
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WaiterTest {
    @OptIn(InternalApi::class)
    private fun failure(errorCode: String) = Result.failure<GetEntityResponse>(
        ServiceException().apply {
            sdkErrorMetadata.attributes[ServiceErrorMetadata.ErrorCode] = errorCode
        }
    )

    private fun success(builderBlock: GetEntityResponse.Builder.() -> Unit) =
        GetEntityResponse.Builder().apply(builderBlock).build().let { success(it) }

    private fun success(response: GetEntityResponse) = Result.success(response)

    @Test
    fun testSuccessResponse(): Unit = runBlocking {
        val response = GetEntityResponse { name = "Foo" }
        val results = listOf(
            failure("NotFound"),
            success(response),
        )
        val client = DefaultWaitersTestClient(results)

        val outcome = client.waitUntilEntityExistsBySuccess { name = "Foo" }
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
        val client = DefaultWaitersTestClient(results)

        val ex = assertFailsWith<ServiceException> {
            client.waitUntilEntityExistsBySuccess { name = "Foo" }
        }
        assertEquals("Unrecoverable", ex.sdkErrorMetadata.errorCode)
    }

    @Test
    fun testSuccessOutput(): Unit = runBlocking {
        val tagsResponse = GetEntityResponse { name = "foo"; tags = mapOf("key" to "foo") }
        val results = listOf(
            success { name = "foo"                               }, // No tags
            success {               tags = mapOf("key" to "foo") }, // No name
            success { name = "foo"; tags = mapOf("key" to "bar") }, // "foo" name mismatches "bar" tag
            success(tagsResponse),                                  // Name matches tag (expected waiter condition)
        )
        val client = DefaultWaitersTestClient(results)

        val outcome = client.waitUntilEntityHasNameTagByOutput { name = "foo" }
        assertEquals(4, outcome.attempts)
        assertEquals(tagsResponse, outcome.getOrThrow())
    }

    @Test
    fun testNumericalComparison(): Unit = runBlocking {
        val successResponse = GetEntityResponse { size = 42 }
        val results = listOf(
            success { },
            success { size = null },
            success { size = 41 },
            success(successResponse),
        )
        val client = DefaultWaitersTestClient(results)

        val outcome = client.waitUntilEntityHasComparableNumericalValues { name = "foo" }
        assertEquals(4, outcome.attempts)
        assertEquals(successResponse, outcome.getOrThrow())
    }
}
