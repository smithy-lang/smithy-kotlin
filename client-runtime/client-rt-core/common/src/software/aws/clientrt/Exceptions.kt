/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt

/**
 * Base exception class for all exceptions thrown by the SDK. Exception may be a client side exception or a service exception
 */
open class SdkBaseException : RuntimeException {

    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)

    open val isRetryable: Boolean = false
}

/**
 * Base exception class for any errors that occur while attempting to use an SDK client to make (Smithy) service calls.
 */
open class ClientException : SdkBaseException {
    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)
}

/**
 * Generic interface that any protocol (e.g. HTTP, MQTT, etc) can extend to provide additional access to
 * protocol specific details.
 */
interface ProtocolResponse

/**
 * ServiceException - Base exception class for any error response returned by a service. Receiving an exception of this
 * type indicates that the caller's request was successfully transmitted to the service and the service sent back an
 * error response.
 */
open class ServiceException : SdkBaseException {

    /**
     * Indicates who (if known) is at fault for this exception.
     */
    enum class ErrorType {
        Client,
        Server,
        Unknown
    }

    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)

    /**
     * The name of the service that sent this error response
     */
    open val serviceName: String = ""

    /**
     * Indicates who is responsible for this exception (caller, service, or unknown)
     */
    open val errorType: ErrorType = ErrorType.Unknown

    /**
     * The human-readable error message provided by the service
     */
    open var errorMessage: String = ""

    /**
     * The protocol response if available (this will differ depending on the underlying protocol e.g. HTTP, MQTT, etc)
     */
    open var protocolResponse: ProtocolResponse? = null
}
