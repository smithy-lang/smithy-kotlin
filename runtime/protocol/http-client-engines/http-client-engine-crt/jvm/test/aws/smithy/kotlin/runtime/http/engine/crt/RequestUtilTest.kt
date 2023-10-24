/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.crt

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequestUtilTest {
    @Test
    fun testExceptionRetryability() {
        setOf(
            // All IO errors are retryable
            "AWS_IO_SOCKET_NETWORK_DOWN",
            "AWS_ERROR_IO_OPERATION_CANCELLED",
            "AWS_IO_DNS_NO_ADDRESS_FOR_HOST",

            // Any connection closure is retryable
            "AWS_ERROR_HTTP_CONNECTION_CLOSED",
            "AWS_ERROR_HTTP_SERVER_CLOSED",
            "AWS_IO_BROKEN_PIPE",

            // All proxy errors are retryable
            "AWS_ERROR_HTTP_PROXY_CONNECT_FAILED",
            "AWS_ERROR_HTTP_PROXY_STRATEGY_NTLM_CHALLENGE_TOKEN_MISSING",
            "AWS_ERROR_HTTP_PROXY_STRATEGY_TOKEN_RETRIEVAL_FAILURE",

            // Any connection manager issues are retryable
            "AWS_ERROR_HTTP_CONNECTION_MANAGER_INVALID_STATE_FOR_ACQUIRE",
            "AWS_ERROR_HTTP_CONNECTION_MANAGER_VENDED_CONNECTION_UNDERFLOW",
            "AWS_ERROR_HTTP_CONNECTION_MANAGER_SHUTTING_DOWN",
        ).forEach { name -> assertTrue(isRetryable(name), "Expected $name to be retryable!") }

        setOf(
            // Any other errors are not retryable
            "AWS_ERROR_HTTP_INVALID_METHOD",
            "AWS_ERROR_PKCS11_CKR_CANCEL",
            "AWS_ERROR_PEM_MALFORMED",

            // Unknown error codes are not retryable
            null,
        ).forEach { name -> assertFalse(isRetryable(name), "Expected $name to not be retryable!") }
    }
}
