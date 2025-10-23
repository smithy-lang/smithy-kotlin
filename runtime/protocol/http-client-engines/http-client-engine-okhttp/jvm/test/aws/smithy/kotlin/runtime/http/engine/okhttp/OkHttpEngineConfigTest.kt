/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpException
import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.toHttpBody
import aws.smithy.kotlin.runtime.net.url.Url
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OkHttpEngineConfigTest {
    @Test
    fun testUserClient() = runTest {
        val userClient = OkHttpClient.Builder().apply {
            addInterceptor { throw DummyOkHttpClientException() }
        }.build()

        val engine = OkHttpEngine(userClient)
        val sdkClient = SdkHttpClient(engine)

        val data = "a".repeat(100)
        val url = Url.parse("https://aws.amazon.com")
        val request = HttpRequest(HttpMethod.POST, url, Headers.Empty, ByteStream.fromString(data).toHttpBody())

        val ex = assertFailsWith<HttpException> {
            sdkClient.call(request)
        }
        assertTrue(ex.cause is DummyOkHttpClientException)
    }

    private class DummyOkHttpClientException : IOException("Custom OkHttpClient interceptor was called")
}
