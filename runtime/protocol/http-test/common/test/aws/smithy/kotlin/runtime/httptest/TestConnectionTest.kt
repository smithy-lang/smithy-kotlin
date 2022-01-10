/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.httptest

import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.content.ByteArrayContent
import aws.smithy.kotlin.runtime.http.readAll
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.complete
import aws.smithy.kotlin.runtime.http.sdkHttpClient
import aws.smithy.kotlin.runtime.testing.runSuspendTest
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class TestConnectionTest {
    @Test
    fun testAssertRequestsSuccess(): Unit = runSuspendTest {
        val engine = buildTestConnection {
            expect {
                request {
                    url.host = "test.com"
                    url.path = "/turtles-all-the-way-down"
                    headers.append("x-foo", "bar")
                    body = ByteArrayContent("tests for your tests".encodeToByteArray())
                }
            }
        }

        val client = sdkHttpClient(engine)

        val req = HttpRequestBuilder().apply {
            url.host = "test.com"
            url.path = "/turtles-all-the-way-down"
            headers.append("x-foo", "bar")
            headers.append("x-qux", "quux")
            body = ByteArrayContent("tests for your tests".encodeToByteArray())
        }
        client.call(req).complete()

        engine.assertRequests()
    }

    @Test
    fun testAssertRequestsUrlDifferent(): Unit = runSuspendTest {
        val engine = buildTestConnection {
            expect {
                request {
                    url.host = "test.com"
                    url.path = "/turtles-all-the-way-down"
                    headers.append("x-foo", "bar")
                    body = ByteArrayContent("tests for your tests".encodeToByteArray())
                }
            }
        }

        val client = sdkHttpClient(engine)

        val req = HttpRequestBuilder().apply {
            url.host = "test.com"
            url.path = "/tests-for-your-tests"
            headers.append("x-foo", "bar")
        }
        client.call(req).complete()

        assertFails {
            engine.assertRequests()
        }.message.shouldContain("URL mismatch")
    }

    @Test
    fun testAssertRequestsMissingHeader(): Unit = runSuspendTest {
        val engine = buildTestConnection {
            expect {
                request {
                    url.host = "test.com"
                    url.path = "/turtles-all-the-way-down"
                    headers.append("x-foo", "bar")
                    headers.append("x-baz", "qux")
                }
            }
        }

        val client = sdkHttpClient(engine)

        val req = HttpRequestBuilder().apply {
            url.host = "test.com"
            url.path = "/turtles-all-the-way-down"
            headers.append("x-foo", "bar")
        }
        client.call(req).complete()

        assertFails {
            engine.assertRequests()
        }.message.shouldContain("header `x-baz` missing value `qux`")
    }

    @Test
    fun testAssertRequestsBodyDifferent(): Unit = runSuspendTest {
        val engine = buildTestConnection {
            expect {
                request {
                    url.host = "test.com"
                    url.path = "/turtles-all-the-way-down"
                    headers.append("x-foo", "bar")
                    body = ByteArrayContent("tests for your tests".encodeToByteArray())
                }
            }
        }

        val client = sdkHttpClient(engine)

        val req = HttpRequestBuilder().apply {
            url.host = "test.com"
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
    fun testAssertRequestsAny(): Unit = runSuspendTest {
        val engine = buildTestConnection {
            expect {
                request {
                    url.host = "test.com"
                    url.path = "/turtles-all-the-way-down"
                    headers.append("x-foo", "bar")
                    body = ByteArrayContent("tests for your tests".encodeToByteArray())
                }
            }
            // ANY request
            expect()
        }

        val client = sdkHttpClient(engine)

        val req = HttpRequestBuilder().apply {
            url.host = "test.com"
            url.path = "/turtles-all-the-way-down"
            headers.append("x-foo", "bar")
            headers.append("x-qux", "quux")
            body = ByteArrayContent("tests for your tests".encodeToByteArray())
        }
        client.call(req).complete()
        client.call(
            HttpRequestBuilder().apply {
                url.host = "test-anything.com"
            }
        )

        engine.assertRequests()
    }

    @Test
    fun testFromJson(): Unit = runSuspendTest {
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
        val client = sdkHttpClient(engine)

        val req = HttpRequestBuilder().apply {
            url.host = "test.aws.com"
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
                url.host = "test-anything.com"
            }
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
