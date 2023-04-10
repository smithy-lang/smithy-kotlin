/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http

import aws.smithy.kotlin.runtime.ErrorMetadata
import aws.smithy.kotlin.runtime.SdkBaseException

/**
 * Base exception class for HTTP errors
 */
public class HttpException(
    message: String? = null,
    cause: Throwable? = null,
    public val errorCode: HttpErrorCode = HttpErrorCode.SDK_UNKNOWN,
    retryable: Boolean = false,
) : SdkBaseException(message ?: cause?.toString(), cause) {

    init {
        sdkErrorMetadata.attributes[ErrorMetadata.Retryable] = retryable || when (errorCode) {
            HttpErrorCode.CONNECT_TIMEOUT, HttpErrorCode.TLS_NEGOTIATION_TIMEOUT -> true
            else -> false
        }
    }

    override fun toString(): String {
        val orig = super.toString()
        return when (errorCode) {
            HttpErrorCode.SDK_UNKNOWN -> orig
            else -> "$orig; HttpErrorCode($errorCode)"
        }
    }
}

public enum class HttpErrorCode {
    /**
     * A connection could not be established within the configured amount of time
     */
    CONNECT_TIMEOUT,

    /**
     * A connection could not be leased from the connection pool after the configured amount of time
     */
    CONNECTION_ACQUIRE_TIMEOUT,

    /**
     * TLS negotiation did not complete within the configure amount of time
     */
    TLS_NEGOTIATION_TIMEOUT,

    /**
     * TLS negotiation failed
     */
    TLS_NEGOTIATION_ERROR,

    /**
     * The connection was closed while the request was in-flight
     */
    CONNECTION_CLOSED,

    /**
     * A timeout has occurred on a socket read or write
     */
    SOCKET_TIMEOUT,

    /**
     * Failed to negotiate the HTTP protocol version with the service
     */
    PROTOCOL_NEGOTIATION_ERROR,

    /**
     * Unknown error
     */
    SDK_UNKNOWN,
}
