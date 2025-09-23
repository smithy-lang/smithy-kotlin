/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.crt

import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.http.toHttpBody
import aws.smithy.kotlin.runtime.net.url.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class RequestUtilTest {
    @Test
    fun testExceptionRetryability() {
        mapOf(
            // All IO errors are retryable
            1050 to "AWS_IO_SOCKET_NETWORK_DOWN",
            1042 to "AWS_ERROR_IO_OPERATION_CANCELLED",
            1060 to "AWS_IO_DNS_NO_ADDRESS_FOR_HOST",

            // Any connection closure is retryable
            2058 to "AWS_ERROR_HTTP_CONNECTION_CLOSED",
            2070 to "AWS_ERROR_HTTP_SERVER_CLOSED",
            1044 to "AWS_IO_BROKEN_PIPE",

            // Specific HTTP errors are retryable
            2071 to "AWS_ERROR_HTTP_PROXY_CONNECT_FAILED",
            2068 to "AWS_ERROR_HTTP_CONNECTION_MANAGER_INVALID_STATE_FOR_ACQUIRE",
            2069 to "AWS_ERROR_HTTP_CONNECTION_MANAGER_VENDED_CONNECTION_UNDERFLOW",
        ).forEach { (code, name) ->
            assertTrue(isRetryable(code, name), "Expected $name to be retryable!")
        }

        mapOf(
            // Any other errors are not retryable
            2053 to "AWS_ERROR_HTTP_INVALID_METHOD",
            1078 to "AWS_ERROR_PKCS11_CKR_CANCEL",
            1179 to "AWS_ERROR_PEM_MALFORMED",

            // Unknown error codes are not retryable
            0 to null,
        ).forEach { (code, name) ->
            assertFalse(isRetryable(code, name), "Expected $name to not be retryable!")
        }
    }

    @Test
    fun testContentLengthHeader() = runTest {
        val data = "a".repeat(100)

        val request = HttpRequest(
            HttpMethod.POST,
            url = Url.parse("https://notarealurl.com"),
            headers = Headers { set("Content-Length", data.length.toString()) },
            body = ByteStream.fromString(data).toHttpBody()
        )

        val crtRequest = request.toCrtRequest(coroutineContext)
        assertEquals(listOf(data.length.toString()), crtRequest.headers.getAll("Content-Length"))
    }
}
