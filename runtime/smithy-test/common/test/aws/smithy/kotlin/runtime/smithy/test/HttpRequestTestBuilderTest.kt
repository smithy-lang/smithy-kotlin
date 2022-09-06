/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.smithy.test

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.http.content.ByteArrayContent
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.headers
import aws.smithy.kotlin.runtime.util.net.Host
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import kotlin.test.assertFails

class HttpRequestTestBuilderTest {

    private val execContext = ExecutionContext()

    @Test
    fun itAssertsHttpMethod() {
        val ex = assertFails {
            httpRequestTest {
                expected {
                    method = HttpMethod.POST
                }
                operation { mockEngine ->
                    val builder = HttpRequest {
                        method = HttpMethod.GET
                    }
                    mockEngine.roundTrip(execContext, builder)
                }
            }
        }
        ex.message.shouldContain("expected method: `POST`; got: `GET`")
    }

    @Test
    fun itAssertsUri() {
        val ex = assertFails {
            httpRequestTest {
                expected {
                    method = HttpMethod.POST
                    uri = "/foo"
                }
                operation { mockEngine ->
                    val builder = HttpRequest {
                        method = HttpMethod.POST
                        url.path = "/bar"
                    }
                    mockEngine.roundTrip(execContext, builder)
                }
            }
        }
        ex.message.shouldContain("expected path: `/foo`; got: `/bar`")
    }

    @Test
    fun itAssertsQueryParameters() {
        val ex = assertFails {
            httpRequestTest {
                expected {
                    method = HttpMethod.POST
                    uri = "/foo"
                    queryParams = listOf("baz" to "quux", "Hi" to "Hello%20there")
                }
                operation { mockEngine ->
                    val request = HttpRequest {
                        method = HttpMethod.POST
                        url.path = "/foo"
                        url.parameters.append("baz", "quux")
                        url.parameters.append("Hi", "Hello")
                    }
                    mockEngine.roundTrip(execContext, request)
                }
            }
        }
        ex.message.shouldContain("expected query name value pair not found: `Hi:Hello%20there`")
    }

    @Test
    fun itAssertsForbiddenQueryParameters() {
        val ex = assertFails {
            httpRequestTest {
                expected {
                    method = HttpMethod.POST
                    uri = "/foo"
                    queryParams = listOf("baz" to "quux", "Hi" to "Hello%20there")
                    forbiddenQueryParams = listOf("foobar")
                }
                operation { mockEngine ->
                    val request = HttpRequest {
                        method = HttpMethod.POST
                        url.path = "/foo"
                        url.parameters.append("baz", "quux")
                        url.parameters.append("Hi", "Hello there")
                        url.parameters.append("foobar", "i am forbidden")
                    }
                    mockEngine.roundTrip(execContext, request)
                }
            }
        }
        ex.message.shouldContain("forbidden query parameter found: `foobar`")
    }

    @Test
    fun itAssertsRequiredQueryParameters() {
        val ex = assertFails {
            httpRequestTest {
                expected {
                    method = HttpMethod.POST
                    uri = "/foo"
                    queryParams = listOf("baz" to "quux", "Hi" to "Hello%20there")
                    forbiddenQueryParams = listOf("foobar")
                    requiredQueryParams = listOf("requiredQuery")
                }
                operation { mockEngine ->
                    val request = HttpRequest {
                        method = HttpMethod.POST
                        url.path = "/foo"
                        url.parameters.append("baz", "quux")
                        url.parameters.append("Hi", "Hello there")
                        url.parameters.append("foobar2", "i am not forbidden")
                    }
                    mockEngine.roundTrip(execContext, request)
                }
            }
        }
        ex.message.shouldContain("required query parameter not found: `requiredQuery`")
    }

    @Test
    fun itAssertsHeaders() {
        val ex = assertFails {
            httpRequestTest {
                expected {
                    method = HttpMethod.POST
                    uri = "/foo"
                    queryParams = listOf("baz" to "quux", "Hi" to "Hello%20there")
                    forbiddenQueryParams = listOf("foobar")
                    requiredQueryParams = listOf("requiredQuery")
                    headers = mapOf(
                        "k1" to "v1",
                        "k2" to "v2",
                    )
                }
                operation { mockEngine ->
                    val request = HttpRequest {
                        method = HttpMethod.POST
                        url.path = "/foo"
                        url.parameters.append("baz", "quux")
                        url.parameters.append("Hi", "Hello there")
                        url.parameters.append("foobar2", "i am not forbidden")
                        url.parameters.append("requiredQuery", "i am required")

                        headers {
                            append("k1", "v1")
                        }
                    }
                    mockEngine.roundTrip(execContext, request)
                }
            }
        }
        ex.message.shouldContain("expected header `k2` has no actual values")
    }

    @Test
    fun itAssertsListsOfHeaders() {
        val ex = assertFails {
            httpRequestTest {
                expected {
                    method = HttpMethod.POST
                    uri = "/foo"
                    headers = mapOf(
                        "k1" to "v1, v2",
                        "k2" to "v3, v4, v5",
                    )
                }
                operation { mockEngine ->
                    val request = HttpRequest {
                        method = HttpMethod.POST
                        url.path = "/foo"
                        headers {
                            appendAll("k1", listOf("v1", "v2"))
                            appendAll("k2", listOf("v3", "v4"))
                        }
                    }
                    mockEngine.roundTrip(execContext, request)
                }
            }
        }
        ex.message.shouldContain("expected header name value pair not equal: `k2:v3, v4, v5`; found: `k2:v3, v4")
    }

    @Test
    fun itAssertsForbiddenHeaders() {
        val ex = assertFails {
            httpRequestTest {
                expected {
                    method = HttpMethod.POST
                    uri = "/foo"
                    queryParams = listOf("baz" to "quux", "Hi" to "Hello%20there")
                    forbiddenQueryParams = listOf("foobar")
                    requiredQueryParams = listOf("requiredQuery")
                    headers = mapOf(
                        "k1" to "v1",
                        "k2" to "v2",
                    )
                    forbiddenHeaders = listOf("forbiddenHeader")
                }
                operation { mockEngine ->
                    val request = HttpRequest {
                        method = HttpMethod.POST
                        url.path = "/foo"
                        url.parameters.append("baz", "quux")
                        url.parameters.append("Hi", "Hello there")
                        url.parameters.append("foobar2", "i am not forbidden")
                        url.parameters.append("requiredQuery", "i am required")

                        headers {
                            append("k1", "v1")
                            append("k2", "v2")
                            append("forbiddenHeader", "i am forbidden")
                        }
                    }
                    mockEngine.roundTrip(execContext, request)
                }
            }
        }
        ex.message.shouldContain("forbidden header found: `forbiddenHeader`")
    }

    @Test
    fun itAssertsRequiredHeaders() {
        val ex = assertFails {
            httpRequestTest {
                expected {
                    method = HttpMethod.POST
                    uri = "/foo"
                    queryParams = listOf("baz" to "quux", "Hi" to "Hello%20there")
                    forbiddenQueryParams = listOf("foobar")
                    requiredQueryParams = listOf("requiredQuery")
                    headers = mapOf(
                        "k1" to "v1",
                        "k2" to "v2",
                    )
                    forbiddenHeaders = listOf("forbiddenHeader")
                    requiredHeaders = listOf("requiredHeader")
                }
                operation { mockEngine ->
                    val request = HttpRequest {
                        method = HttpMethod.POST
                        url.path = "/foo"
                        url.parameters.append("baz", "quux")
                        url.parameters.append("Hi", "Hello there")
                        url.parameters.append("foobar2", "i am not forbidden")
                        url.parameters.append("requiredQuery", "i am required")

                        headers {
                            append("k1", "v1")
                            append("k2", "v2")
                            append("forbiddenHeader2", "i am not forbidden")
                        }
                    }
                    mockEngine.roundTrip(execContext, request)
                }
            }
        }
        ex.message.shouldContain("expected required header not found: `requiredHeader`")
    }

    @Test
    fun itFailsWhenBodyAssertFunctionIsMissing() {
        val ex = assertFails {
            httpRequestTest {
                expected {
                    body = "hello testing"
                }
                operation { mockEngine ->
                    // no actual body should not make it to our assertEquals but it should still fail (invalid test setup)
                    val request = HttpRequest {
                    }
                    mockEngine.roundTrip(execContext, request)
                }
            }
        }

        ex.message.shouldContain("body assertion function is required if an expected body is defined")
    }

    @Test
    fun itCallsBodyAssertFunction() {
        val ex = assertFails {
            httpRequestTest {
                expected {
                    body = "hello testing"
                    bodyAssert = ::assertBytesEqual
                }
                operation { mockEngine ->
                    // no actual body should not make it to our assertEquals but it should still fail (invalid test setup)
                    val request = HttpRequest {
                        body = ByteArrayContent("do not pass go".encodeToByteArray())
                    }
                    mockEngine.roundTrip(execContext, request)
                }
            }
        }
        ex.message.shouldContain("actual bytes read does not match expected")
    }

    @Test
    fun itAssertsHostWhenSet() {
        val ex = assertFails {
            httpRequestTest {
                expected {
                    method = HttpMethod.POST
                    resolvedHost = "foo.example.com"
                }
                operation { mockEngine ->
                    val request = HttpRequest {
                        method = HttpMethod.POST
                        url.host = Host.Domain("bar.example.com")
                    }
                    mockEngine.roundTrip(execContext, request)
                }
            }
        }
        ex.message.shouldContain("expected host: `foo.example.com`; got: `bar.example.com`")
    }
}
