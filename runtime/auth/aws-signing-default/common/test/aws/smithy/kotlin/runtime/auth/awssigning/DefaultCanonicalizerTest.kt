/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awssigning.tests.DEFAULT_TEST_CREDENTIALS
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.request.*
import aws.smithy.kotlin.runtime.net.Host
import aws.smithy.kotlin.runtime.net.url.Url
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class DefaultCanonicalizerTest {
    // Test adapted from https://docs.aws.amazon.com/general/latest/gr/sigv4-create-canonical-request.html
    @Test
    fun testCanonicalize() = runTest {
        val request = HttpRequest {
            method = HttpMethod.GET
            url {
                host = Host.Domain("iam.amazonaws.com")
                parameters.decodedParameters {
                    add("Action", "ListUsers")
                    add("Version", "2010-05-08")
                }
            }
            headers {
                set("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            }
            body = HttpBody.Empty
        }

        val signingDateString = "20150830T123600Z"
        val config = AwsSigningConfig {
            region = "foo"
            service = "bar"
            signingDate = Instant.fromIso8601(signingDateString)
            credentials = Credentials("foo", "bar") // anything without a session token set
        }

        val canonicalizer = Canonicalizer.Default
        val actual = canonicalizer.canonicalRequest(request, config)

        val expectedHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertEquals(expectedHash, actual.hash)

        val expectedSignedHeaders = "content-type;host;x-amz-date"
        assertEquals(expectedSignedHeaders, actual.signedHeaders)

        val expectedRequestString = """
            GET
            /
            Action=ListUsers&Version=2010-05-08
            content-type:application/x-www-form-urlencoded; charset=utf-8
            host:iam.amazonaws.com
            x-amz-date:20150830T123600Z

            $expectedSignedHeaders
            $expectedHash
        """.trimIndent()
        assertEquals(expectedRequestString, actual.requestString)

        assertEquals(request.method, actual.request.method)
        assertEquals(request.url.toString(), actual.request.url.build().toString())
        assertEquals(request.body, actual.request.body)

        val expectedHeaders = Headers {
            appendAll(request.headers)
            append("Host", request.url.host.toString())
            append("X-Amz-Date", signingDateString)
        }.entries()
        assertEquals(expectedHeaders, actual.request.headers.entries())
    }

    // Targeted test for proper URI path escaping. See https://github.com/awslabs/smithy-kotlin/issues/657
    @Test
    fun testEscapablePath() {
        val uri = Url.Builder()
        uri.path.encoded = "/2013-04-01/healthcheck/foo%3Cbar%3Ebaz%3C%2Fbar%3E"

        val config = AwsSigningConfig {
            normalizeUriPath = true
            useDoubleUriEncode = true
            region = "the-moon"
            service = "landing-pad"
            credentials = DEFAULT_TEST_CREDENTIALS
        }

        assertEquals("/2013-04-01/healthcheck/foo%253Cbar%253Ebaz%253C%252Fbar%253E", uri.canonicalPath(config))
    }

    @Test
    fun testCanonicalPath() {
        val config = AwsSigningConfig {
            normalizeUriPath = true
            useDoubleUriEncode = true
            region = "the-moon"
            service = "landing-pad"
            credentials = DEFAULT_TEST_CREDENTIALS
        }

        val uri = Url.Builder()
        uri.path.encoded = "/foo/@bar/baz%3Cqux%3Aquux"
        assertEquals("/foo/%40bar/baz%253Cqux%253Aquux", uri.canonicalPath(config))
    }

    @Test
    fun testCanonicalQueryParams() {
        Url.Builder().apply {
            parameters {
                decodedParameters {
                    addAll("foo", listOf("banana", "apple", "cherry"))
                    addAll("bar", listOf("elderberry", "d@te"))
                }
                assertEquals("?foo=banana&foo=apple&foo=cherry&bar=elderberry&bar=d@te", decoded)
            }
            assertEquals("bar=d%40te&bar=elderberry&foo=apple&foo=banana&foo=cherry", canonicalQueryParams())
        }
    }

    @Test
    fun testUnsignedHeaders() = runTest {
        val request = HttpRequest {
            method = HttpMethod.GET
            url { host = Host.Domain("bar.amazonaws.com") }
            headers {
                // These should be signed
                set("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                set("x-amz-user-agent", "baz")

                // These should not be signed
                set("Expect", "100-continue")
                set("X-Amzn-Trace-Id", "qux")
            }
            body = HttpBody.Empty
        }

        val signingDateString = "20150830T123600Z"
        val config = AwsSigningConfig {
            region = "foo"
            service = "bar"
            signingDate = Instant.fromIso8601(signingDateString)
            credentials = Credentials("foo", "bar") // anything without a session token set
        }

        val canonicalizer = Canonicalizer.Default
        val actual = canonicalizer.canonicalRequest(request, config)

        val expectedSignedHeaders = "content-type;host;x-amz-date;x-amz-user-agent"
        assertEquals(expectedSignedHeaders, actual.signedHeaders)
    }

    @Test
    fun testCustomPort() = runTest {
        val request = HttpRequest {
            method = HttpMethod.GET
            url {
                host = Host.Domain("bar.amazonaws.com")
                port = 8080
            }
            body = HttpBody.Empty
        }

        val signingDateString = "20150830T123600Z"
        val config = AwsSigningConfig {
            region = "foo"
            service = "bar"
            signingDate = Instant.fromIso8601(signingDateString)
            credentials = Credentials("foo", "bar") // anything without a session token set
        }

        val canonicalizer = Canonicalizer.Default
        val actual = canonicalizer.canonicalRequest(request, config)

        assertContains(actual.requestString, "host:bar.amazonaws.com:8080")
    }
}
