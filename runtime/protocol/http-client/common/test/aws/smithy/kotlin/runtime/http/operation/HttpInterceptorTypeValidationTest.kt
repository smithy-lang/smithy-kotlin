/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.client.*
import aws.smithy.kotlin.runtime.http.interceptors.HttpInterceptor
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.IllegalStateException
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class HttpInterceptorTypeValidationTest {
    @Test
    fun testModifyBeforeSerializationTypeFailure() = runTest {
        val i1 = object : HttpInterceptor {
            override suspend fun modifyBeforeSerialization(context: RequestInterceptorContext<Any>): Any {
                val input = assertIs<TestInput>(context.request)
                assertEquals("initial", input.value)
                return TestInput("modified")
            }
        }

        val i2 = object : HttpInterceptor {
            override suspend fun modifyBeforeSerialization(context: RequestInterceptorContext<Any>): Any {
                val input = assertIs<TestInput>(context.request)
                assertEquals("modified", input.value)
                return TestOutput("wrong")
            }
        }

        val input = TestInput("initial")
        val op = newTestOperation<TestInput, Unit>(HttpRequestBuilder(), Unit)
        val client = newMockHttpClient()
        val ex = assertFailsWith<IllegalStateException> {
            roundTripWithInterceptors(input, op, client, i1, i2)
        }

        ex.message.shouldContain("modifyBeforeSerialization invalid type conversion: found class aws.smithy.kotlin.runtime.http.operation.TestOutput; expected class aws.smithy.kotlin.runtime.http.operation.TestInput")
    }

    @Test
    fun testModifyBeforeAttemptCompletionTypeFailure() = runTest {
        val i1 = object : HttpInterceptor {
            override suspend fun modifyBeforeAttemptCompletion(context: ResponseInterceptorContext<Any, Any, HttpRequest, HttpResponse?>): Result<Any> {
                assertIs<TestOutput>(context.response.getOrThrow())
                return Result.success(TestOutput("modified"))
            }
        }

        val i2 = object : HttpInterceptor {
            override suspend fun modifyBeforeAttemptCompletion(context: ResponseInterceptorContext<Any, Any, HttpRequest, HttpResponse?>): Result<Any> {
                val output = assertIs<TestOutput>(context.response.getOrThrow())
                assertEquals("modified", output.value)
                return Result.success("wrong")
            }
        }

        val output = TestOutput("initial")
        val op = newTestOperation<Unit, TestOutput>(HttpRequestBuilder(), output)
        val client = newMockHttpClient()
        val ex = assertFailsWith<IllegalStateException> {
            roundTripWithInterceptors(Unit, op, client, i1, i2)
        }

        ex.message.shouldContain("modifyBeforeAttemptCompletion invalid type conversion: found class kotlin.String; expected class aws.smithy.kotlin.runtime.http.operation.TestOutput")
    }

    @Test
    fun testModifyBeforeCompletionTypeFailure() = runTest {
        val i1 = object : HttpInterceptor {
            override suspend fun modifyBeforeCompletion(context: ResponseInterceptorContext<Any, Any, HttpRequest?, HttpResponse?>): Result<Any> {
                assertIs<TestOutput>(context.response.getOrThrow())
                return Result.success(TestOutput("modified"))
            }
        }

        val i2 = object : HttpInterceptor {
            override suspend fun modifyBeforeCompletion(context: ResponseInterceptorContext<Any, Any, HttpRequest?, HttpResponse?>): Result<Any> {
                val output = assertIs<TestOutput>(context.response.getOrThrow())
                assertEquals("modified", output.value)
                return Result.success("wrong")
            }
        }

        val output = TestOutput("initial")
        val op = newTestOperation<Unit, TestOutput>(HttpRequestBuilder(), output)
        val client = newMockHttpClient()
        val ex = assertFailsWith<IllegalStateException> {
            roundTripWithInterceptors(Unit, op, client, i1, i2)
        }

        ex.message.shouldContain("modifyBeforeCompletion invalid type conversion: found class kotlin.String; expected class aws.smithy.kotlin.runtime.http.operation.TestOutput")
    }
}
