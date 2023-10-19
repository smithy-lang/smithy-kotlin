/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.http.HttpErrorCode
import aws.smithy.kotlin.runtime.http.HttpException
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OkHttpExceptionTest {
    data class ExceptionTest(
        val ex: Exception,
        val expectedError: HttpErrorCode,
    )

    @Test
    fun testMapExceptions() {
        val tests = listOf(
            ExceptionTest(IOException("unknown"), expectedError = HttpErrorCode.SDK_UNKNOWN),
            ExceptionTest(IOException(SocketTimeoutException("connect timeout")), expectedError = HttpErrorCode.CONNECT_TIMEOUT),
            ExceptionTest(IOException().apply { addSuppressed(SocketTimeoutException("connect timeout")) }, expectedError = HttpErrorCode.CONNECT_TIMEOUT),
            ExceptionTest(IOException(SocketTimeoutException("read timeout")), expectedError = HttpErrorCode.SOCKET_TIMEOUT),
            ExceptionTest(IOException().apply { addSuppressed(SocketTimeoutException("read timeout")) }, expectedError = HttpErrorCode.SOCKET_TIMEOUT),
            ExceptionTest(SocketTimeoutException("read timeout"), expectedError = HttpErrorCode.SOCKET_TIMEOUT),
            ExceptionTest(IOException(SSLHandshakeException("negotiate error")), expectedError = HttpErrorCode.TLS_NEGOTIATION_ERROR),
            ExceptionTest(ConnectException("test connect error"), expectedError = HttpErrorCode.SDK_UNKNOWN),
            // see https://github.com/awslabs/aws-sdk-kotlin/issues/905
            ExceptionTest(IOException("unexpected end of stream on https://test.aws.com", EOFException("\\n not found: limit=0 content=...")), expectedError = HttpErrorCode.CONNECTION_CLOSED),
        )

        for (test in tests) {
            val ex = assertFailsWith<HttpException> {
                mapOkHttpExceptions {
                    throw test.ex
                }
            }

            assertEquals(test.expectedError, ex.errorCode, "ex=$ex; ${ex.suppressedExceptions}; cause=${ex.cause}; causeSuppressed=${ex.cause?.suppressedExceptions}")
            assertTrue(ex.sdkErrorMetadata.isRetryable)
        }
    }
}
