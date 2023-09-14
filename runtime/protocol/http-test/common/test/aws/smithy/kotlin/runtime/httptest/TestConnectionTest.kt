/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.httptest

import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.content.ByteArrayContent
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.net.Host
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

@OptIn(ExperimentalCoroutinesApi::class)
class TestConnectionTest {
    @Test
    fun testAssertRequestsSuccess() = runTest {
        val engine = buildTestConnection {
            expect {
                request {
                    url.host = Host.Domain("test.com")
                    url.path = "/turtles-all-the-way-down"
                    headers.append("x-foo", "bar")
                    body = ByteArrayContent("tests for your tests".encodeToByteArray())
                }
            }
        }

        val client = SdkHttpClient(engine)

        val req = HttpRequestBuilder().apply {
            url.host = Host.Domain("test.com")
            url.path = "/turtles-all-the-way-down"
            headers.append("x-foo", "bar")
            headers.append("x-qux", "quux")
            body = ByteArrayContent("tests for your tests".encodeToByteArray())
        }
        client.call(req).complete()

        engine.assertRequests()
    }

    @Test
    fun testAssertRequestsUrlDifferent() = runTest {
        val engine = buildTestConnection {
            expect {
                request {
                    url.host = Host.Domain("test.com")
                    url.path = "/turtles-all-the-way-down"
                    headers.append("x-foo", "bar")
                    body = ByteArrayContent("tests for your tests".encodeToByteArray())
                }
            }
        }

        val client = SdkHttpClient(engine)

        val req = HttpRequestBuilder().apply {
            url.host = Host.Domain("test.com")
            url.path = "/tests-for-your-tests"
            headers.append("x-foo", "bar")
        }
        client.call(req).complete()

        assertFails {
            engine.assertRequests()
        }.message.shouldContain("URL mismatch")
    }

    @Test
    fun testAssertRequestsMissingHeader() = runTest {
        val engine = buildTestConnection {
            expect {
                request {
                    url.host = Host.Domain("test.com")
                    url.path = "/turtles-all-the-way-down"
                    headers.append("x-foo", "bar")
                    headers.append("x-baz", "qux")
                }
            }
        }

        val client = SdkHttpClient(engine)

        val req = HttpRequestBuilder().apply {
            url.host = Host.Domain("test.com")
            url.path = "/turtles-all-the-way-down"
            headers.append("x-foo", "bar")
        }
        client.call(req).complete()

        assertFails {
            engine.assertRequests()
        }.message.shouldContain("header `x-baz` missing value `qux`")
    }

    @Test
    fun testAssertRequestsBodyDifferent() = runTest {
        val engine = buildTestConnection {
            expect {
                request {
                    url.host = Host.Domain("test.com")
                    url.path = "/turtles-all-the-way-down"
                    headers.append("x-foo", "bar")
                    body = ByteArrayContent("tests for your tests".encodeToByteArray())
                }
            }
        }

        val client = SdkHttpClient(engine)

        val req = HttpRequestBuilder().apply {
            url.host = Host.Domain("test.com")
            url.path = "/turtles-all-the-way-down"
            headers.append("x-foo", "bar")
            body = ByteArrayContent("tests are good".encodeToByteArray())
        }
        client.call(req).complete()

        assertFails {
            engine.assertRequests()
        }.message.shouldContain("body mismatch")
    }

    @Test
    fun testAssertRequestsAny() = runTest {
        val engine = buildTestConnection {
            expect {
                request {
                    url.host = Host.Domain("test.com")
                    url.path = "/turtles-all-the-way-down"
                    headers.append("x-foo", "bar")
                    body = ByteArrayContent("tests for your tests".encodeToByteArray())
                }
            }
            // ANY request
            expect()
        }

        val client = SdkHttpClient(engine)

        val req = HttpRequestBuilder().apply {
            url.host = Host.Domain("test.com")
            url.path = "/turtles-all-the-way-down"
            headers.append("x-foo", "bar")
            headers.append("x-qux", "quux")
            body = ByteArrayContent("tests for your tests".encodeToByteArray())
        }
        client.call(req).complete()
        client.call(
            HttpRequestBuilder().apply {
                url.host = Host.Domain("test-anything.com")
            },
        )

        engine.assertRequests()
    }

    @Test
    fun testFromJson() = runTest {
        // language=JSON
        val data = """
        [
          {
            "request": {
              "method": "POST",
              "uri": "https://test.aws.com/turtles-all-the-way-down?q1=v1",
              "headers": {
                  "foo": "bar",
                  "baz": ["one", "two"]
              },
              "body": "tests for your tests"
            },
            "response": {
              "status": 200,
              "headers": {
                "qux": "quux"
              },
              "body": "response-body-1"
            }
          },
          {
            "request": null,
            "response": {
              "status": 400,
              "headers": {
                "foo": "bar"
              },
              "body": "response-body-2"
            }
          }
        ]
        """.trimIndent()

        val engine = TestConnection.fromJson(data)
        val client = SdkHttpClient(engine)

        val req = HttpRequestBuilder().apply {
            method = HttpMethod.POST
            url.host = Host.Domain("test.aws.com")
            url.path = "/turtles-all-the-way-down"
            url.parameters.append("q1", "v1")
            headers.append("foo", "bar")
            headers.appendAll("baz", listOf("one", "two"))
            body = ByteArrayContent("tests for your tests".encodeToByteArray())
        }

        val call1 = client.call(req)
        call1.complete()

        val call2 = client.call(
            HttpRequestBuilder().apply {
                url.host = Host.Domain("test-anything.com")
            },
        )
        call2.complete()

        engine.assertRequests()

        assertEquals(HttpStatusCode.OK, call1.response.status)
        assertEquals("quux", call1.response.headers["qux"])
        assertEquals("response-body-1", call1.response.body.readAll()?.decodeToString())

        assertEquals(HttpStatusCode.BadRequest, call2.response.status)
        assertEquals("bar", call2.response.headers["foo"])
        assertEquals("response-body-2", call2.response.body.readAll()?.decodeToString())
    }
}
