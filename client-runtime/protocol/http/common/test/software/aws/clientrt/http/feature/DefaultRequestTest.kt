/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.http.feature

import kotlin.test.Test
import kotlin.test.assertEquals
import software.aws.clientrt.http.HttpMethod
import software.aws.clientrt.http.engine.HttpClientEngine
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.response.HttpResponse
import software.aws.clientrt.http.sdkHttpClient
import software.aws.clientrt.testing.runSuspendTest

class DefaultRequestTest {
    @Test
    fun `it sets defaults`() = runSuspendTest {
        val mockEngine = object : HttpClientEngine {
            override suspend fun roundTrip(requestBuilder: HttpRequestBuilder): HttpResponse { throw NotImplementedError() }
        }
        val client = sdkHttpClient(mockEngine) {
            install(DefaultRequest) {
                method = HttpMethod.POST
                url.host = "localhost"
                url.port = 3000
                headers.append("User-Agent", "MTTUserAgent")
            }
        }

        val builder = HttpRequestBuilder()
        val subject = 1 // doesn't matter for test
        client.requestPipeline.execute(builder, subject)
        assertEquals(HttpMethod.POST, builder.method)
        assertEquals("localhost", builder.url.host)
        assertEquals(3000, builder.url.port)
        assertEquals("MTTUserAgent", builder.headers["User-Agent"])
    }
}
