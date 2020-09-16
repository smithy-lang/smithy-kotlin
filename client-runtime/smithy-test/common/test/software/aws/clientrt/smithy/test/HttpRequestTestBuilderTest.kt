/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.smithy.test

import io.kotest.matchers.string.shouldContain
import io.ktor.utils.io.core.toByteArray
import kotlin.test.Test
import kotlin.test.assertFails
import software.aws.clientrt.http.HttpMethod
import software.aws.clientrt.http.content.ByteArrayContent
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.request.headers

class HttpRequestTestBuilderTest {

    @Test
    fun `it asserts HttpMethod`() {
        val ex = assertFails {
            httpRequestTest {
                expected {
                    method = HttpMethod.POST
                }
                operation { mockEngine ->
                    val builder = HttpRequestBuilder().apply {
                        method = HttpMethod.GET
                    }
                    mockEngine.roundTrip(builder)
                }
            }
        }
        ex.message.shouldContain("expected method: `POST`; got: `GET`")
    }

    @Test
    fun `it asserts uri`() {
        val ex = assertFails {
            httpRequestTest {
                expected {
                    method = HttpMethod.POST
                    uri = "/foo"
                }
                operation { mockEngine ->
                    val builder = HttpRequestBuilder().apply {
                        method = HttpMethod.POST
                        url.path = "/bar"
                    }
                    mockEngine.roundTrip(builder)
                }
            }
        }
        ex.message.shouldContain("expected path: `/foo`; got: `/bar`")
    }

    @Test
    fun `it encodes uri in mock engine`() {
        httpRequestTest {
            expected {
                method = HttpMethod.POST
                // expectations come in already encoded
                uri = "/foo/2019-12-16T23%3A48%3A18Z"
            }
            operation { mockEngine ->
                val builder = HttpRequestBuilder().apply {
                    method = HttpMethod.POST
                    // serializers don't need to worry about URL encoding, that is the engines job (or the wrapper
                    // depending on the engine)
                    url.path = "/foo/2019-12-16T23:48:18Z"
                }
                mockEngine.roundTrip(builder)
            }
        }
    }

    @Test
    fun `it asserts query parameters`() {
        val ex = assertFails {
            httpRequestTest {
                expected {
                    method = HttpMethod.POST
                    uri = "/foo"
                    queryParams = listOf("baz" to "quux", "Hi" to "Hello%20there")
                }
                operation { mockEngine ->
                    val builder = HttpRequestBuilder().apply {
                        method = HttpMethod.POST
                        url.path = "/foo"
                        url.parameters.append("baz", "quux")
                        url.parameters.append("Hi", "Hello")
                    }
                    mockEngine.roundTrip(builder)
                }
            }
        }
        ex.message.shouldContain("expected query name value pair not found: `Hi:Hello%20there`")
    }

    @Test
    fun `it asserts forbidden query parameters`() {
        val ex = assertFails {
            httpRequestTest {
                expected {
                    method = HttpMethod.POST
                    uri = "/foo"
                    queryParams = listOf("baz" to "quux", "Hi" to "Hello%20there")
                    forbiddenQueryParams = listOf("foobar")
                }
                operation { mockEngine ->
                    val builder = HttpRequestBuilder().apply {
                        method = HttpMethod.POST
                        url.path = "/foo"
                        url.parameters.append("baz", "quux")
                        url.parameters.append("Hi", "Hello there")
                        url.parameters.append("foobar", "i am forbidden")
                    }
                    mockEngine.roundTrip(builder)
                }
            }
        }
        ex.message.shouldContain("forbidden query parameter found: `foobar`")
    }

    @Test
    fun `it asserts required query parameters`() {
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
                    val builder = HttpRequestBuilder().apply {
                        method = HttpMethod.POST
                        url.path = "/foo"
                        url.parameters.append("baz", "quux")
                        url.parameters.append("Hi", "Hello there")
                        url.parameters.append("foobar2", "i am not forbidden")
                    }
                    mockEngine.roundTrip(builder)
                }
            }
        }
        ex.message.shouldContain("required query parameter not found: `requiredQuery`")
    }

    @Test
    fun `it asserts headers`() {
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
                        "k2" to "v2"
                    )
                }
                operation { mockEngine ->
                    val builder = HttpRequestBuilder().apply {
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
                    mockEngine.roundTrip(builder)
                }
            }
        }
        ex.message.shouldContain("expected header `k2` has no actual values")
    }

    @Test
    fun `it asserts lists of headers`() {
        val ex = assertFails {
            httpRequestTest {
                expected {
                    method = HttpMethod.POST
                    uri = "/foo"
                    headers = mapOf(
                        "k1" to "v1, v2",
                        "k2" to "v3, v4, v5"
                    )
                }
                operation { mockEngine ->
                    val builder = HttpRequestBuilder().apply {
                        method = HttpMethod.POST
                        url.path = "/foo"
                        headers {
                            appendAll("k1", listOf("v1", "v2"))
                            appendAll("k2", listOf("v3", "v4"))
                        }
                    }
                    mockEngine.roundTrip(builder)
                }
            }
        }
        ex.message.shouldContain("expected header name value pair not equal: `k2:v3, v4, v5`; found: `k2:v3, v4")
    }

    @Test
    fun `it asserts forbidden headers`() {
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
                        "k2" to "v2"
                    )
                    forbiddenHeaders = listOf("forbiddenHeader")
                }
                operation { mockEngine ->
                    val builder = HttpRequestBuilder().apply {
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
                    mockEngine.roundTrip(builder)
                }
            }
        }
        ex.message.shouldContain("forbidden header found: `forbiddenHeader`")
    }

    @Test
    fun `it asserts required headers`() {
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
                        "k2" to "v2"
                    )
                    forbiddenHeaders = listOf("forbiddenHeader")
                    requiredHeaders = listOf("requiredHeader")
                }
                operation { mockEngine ->
                    val builder = HttpRequestBuilder().apply {
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
                    mockEngine.roundTrip(builder)
                }
            }
        }
        ex.message.shouldContain("expected required header not found: `requiredHeader`")
    }

    @Test
    fun `it fails when body assert function is missing`() {
        val ex = assertFails {
            httpRequestTest {
                expected {
                    body = "hello testing"
                }
                operation { mockEngine ->
                    // no actual body should not make it to our assertEquals but it should still fail (invalid test setup)
                    val builder = HttpRequestBuilder().apply {
                    }
                    mockEngine.roundTrip(builder)
                }
            }
        }

        ex.message.shouldContain("body assertion function is required if an expected body is defined")
    }

    @Test
    fun `it calls bodyAssert function`() {
        val ex = assertFails {
            httpRequestTest {
                expected {
                    body = "hello testing"
                    bodyAssert = ::assertBytesEqual
                }
                operation { mockEngine ->
                    // no actual body should not make it to our assertEquals but it should still fail (invalid test setup)
                    val builder = HttpRequestBuilder().apply {
                        body = ByteArrayContent("do not pass go".toByteArray())
                    }
                    mockEngine.roundTrip(builder)
                }
            }
        }
        ex.message.shouldContain("actual bytes read does not match expected")
    }
}
