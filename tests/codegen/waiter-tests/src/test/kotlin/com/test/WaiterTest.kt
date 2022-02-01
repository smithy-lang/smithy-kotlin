/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

import aws.smithy.kotlin.runtime.ServiceErrorMetadata
import aws.smithy.kotlin.runtime.ServiceException
import aws.smithy.kotlin.runtime.util.InternalApi
import com.test.TestLambdaClient
import com.test.model.GetFunctionResponse
import com.test.waiters.waitUntilFunctionExistsBySuccess
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class WaiterTest {
    @OptIn(InternalApi::class)
    private val notFoundException = ServiceException().apply {
        sdkErrorMetadata.attributes[ServiceErrorMetadata.ErrorCode] = "NotFound"
    }

    @Test
    fun testSuccessResponse() = runBlocking {
        val results = listOf(
            Result.failure(notFoundException),
            Result.success(GetFunctionResponse { name = "Foo" }),
        )
        val client = TestLambdaClient(results)

        client.waitUntilFunctionExistsBySuccess { name = "Foo" }
    }
}
