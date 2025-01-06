/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.smithy.test

import aws.smithy.kotlin.runtime.IgnoreNative
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.headers
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.net.Host
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import io.kotest.matchers.string.shouldContain
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertFails

class HttpRequestTestBuilderTest {

    private val execContext = ExecutionContext()

    @IgnoreNative // FIXME Re-enable after Kotlin/Native implementation
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

    @IgnoreNative // FIXME Re-enable after Kotlin/Native implementation
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
                        url.path.encoded = "/bar"
                    }
                    mockEngine.roundTrip(execContext, builder)
                }
            }
        }
        ex.message.shouldContain("expected path: `/foo`; got: `/bar`")
    }

    @IgnoreNative // FIXME Re-enable after Kotlin/Native implementation
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

                        url {
                            path.encoded = "/foo"
                            parameters.decodedParameters {
                                add("baz", "quux")
                                add("Hi", "Hello")
                            }
                        }
                    }
                    mockEngine.roundTrip(execContext, request)
                }
            }
        }
        ex.message.shouldContain("Query parameter `Hi` does not contain expected value `Hello%20there`. Actual values: [Hello]")
    }

    @IgnoreNative // FIXME Re-enable after Kotlin/Native implementation
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

                        url {
                            path.encoded = "/foo"
                            parameters.decodedParameters {
                                add("baz", "quux")
                                add("Hi", "Hello there")
                                add("foobar", "i am forbidden")
                            }
                        }
                    }
                    mockEngine.roundTrip(execContext, request)
                }
            }
        }
        ex.message.shouldContain("forbidden query parameter found: `foobar`")
    }

    @IgnoreNative // FIXME Re-enable after Kotlin/Native implementation
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

                        url {
                            path.encoded = "/foo"
                            parameters.decodedParameters {
                                add("baz", "quux")
                                add("Hi", "Hello there")
                                add("foobar2", "i am not forbidden")
                            }
                        }
                    }
                    mockEngine.roundTrip(execContext, request)
                }
            }
        }
        ex.message.shouldContain("required query parameter not found: `requiredQuery`")
    }

    @IgnoreNative // FIXME Re-enable after Kotlin/Native implementation
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

                        url {
                            path.encoded = "/foo"
                            parameters.decodedParameters {
                                add("baz", "quux")
                                add("Hi", "Hello there")
                                add("foobar2", "i am not forbidden")
                                add("requiredQuery", "i am required")
                            }
                        }

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

    @IgnoreNative // FIXME Re-enable after Kotlin/Native implementation
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
                        url.path.encoded = "/foo"
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

    @IgnoreNative // FIXME Re-enable after Kotlin/Native implementation
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

                        url {
                            path.encoded = "/foo"
                            parameters.decodedParameters {
                                add("baz", "quux")
                                add("Hi", "Hello there")
                                add("foobar2", "i am not forbidden")
                                add("requiredQuery", "i am required")
                            }
                        }

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

    @IgnoreNative // FIXME Re-enable after Kotlin/Native implementation
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

                        url {
                            path.encoded = "/foo"
                            parameters.decodedParameters {
                                add("baz", "quux")
                                add("Hi", "Hello there")
                                add("foobar2", "i am not forbidden")
                                add("requiredQuery", "i am required")
                            }
                        }

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

    @IgnoreNative // FIXME Re-enable after Kotlin/Native implementation
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

    @IgnoreNative // FIXME Re-enable after Kotlin/Native implementation
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
                        body = HttpBody.fromBytes("do not pass go".encodeToByteArray())
                    }
                    mockEngine.roundTrip(execContext, request)
                }
            }
        }
        ex.message.shouldContain("actual bytes read does not match expected")
    }

    @IgnoreNative // FIXME Re-enable after Kotlin/Native implementation
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
