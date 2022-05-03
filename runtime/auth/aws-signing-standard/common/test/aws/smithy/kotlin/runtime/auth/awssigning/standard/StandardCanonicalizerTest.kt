/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.auth.awssigning.standard

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigningConfig
import aws.smithy.kotlin.runtime.auth.awssigning.tests.testCredentialsProvider
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.http.parameters
import aws.smithy.kotlin.runtime.http.request.*
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class StandardCanonicalizerTest {
    // Test adapted from https://docs.aws.amazon.com/general/latest/gr/sigv4-create-canonical-request.html
    @Test
    fun testCanonicalize() = runTest {
        val request = HttpRequest {
            method = HttpMethod.GET
            url {
                host = "iam.amazonaws.com"
                path = ""
                parameters {
                    set("Action", "ListUsers")
                    set("Version", "2010-05-08")
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
            credentialsProvider = testCredentialsProvider
        }
        val credentials = Credentials("foo", "bar") // anything without a session token set

        val canonicalizer = Canonicalizer.Default
        val actual = canonicalizer.canonicalRequest(request, config, credentials)

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
            append("Host", request.url.host)
            append("X-Amz-Date", signingDateString)
        }.entries()
        assertEquals(expectedHeaders, actual.request.headers.entries())
    }
}
