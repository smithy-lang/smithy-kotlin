/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime

import aws.smithy.kotlin.runtime.client.SdkClientOption
import aws.smithy.kotlin.runtime.util.AttributeKey
import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * Additional metadata about an error
 */
public open class ErrorMetadata {
    @InternalApi
    public val attributes: Attributes = Attributes()

    public companion object {
        /**
         * Set if an error is retryable
         */
        public val Retryable: AttributeKey<Boolean> = AttributeKey("Retryable")

        /**
         * Set if an error represents a throttling condition
         */
        public val ThrottlingError: AttributeKey<Boolean> = AttributeKey("ThrottlingError")
    }

    public val isRetryable: Boolean
        get() = attributes.getOrNull(Retryable) ?: false

    public val isThrottling: Boolean
        get() = attributes.getOrNull(ThrottlingError) ?: false
}

/**
 * Base exception class for all exceptions thrown by the SDK. Exception may be a client side exception or a service exception
 */
public open class SdkBaseException : RuntimeException {

    public constructor() : super()

    public constructor(message: String?) : super(message)

    public constructor(message: String?, cause: Throwable?) : super(message, cause)

    public constructor(cause: Throwable?) : super(cause)

    /**
     * Additional metadata about the error
     */
    public open val sdkErrorMetadata: ErrorMetadata = ErrorMetadata()
}

/**
 * Base exception class for any errors that occur while attempting to use an SDK client to make (Smithy) service calls.
 */
public open class ClientException : SdkBaseException {
    public constructor() : super()

    public constructor(message: String?) : super(message)

    public constructor(message: String?, cause: Throwable?) : super(message, cause)

    public constructor(cause: Throwable?) : super(cause)
}

/**
 * Generic interface that any protocol (e.g. HTTP, MQTT, etc) can extend to provide additional access to
 * protocol specific details.
 */
public interface ProtocolResponse

private object EmptyProtocolResponse : ProtocolResponse

public open class ServiceErrorMetadata : ErrorMetadata() {
    public companion object {
        public val ErrorCode: AttributeKey<String> = AttributeKey("ErrorCode")
        public val ErrorType: AttributeKey<ServiceException.ErrorType> = AttributeKey("ErrorType")
        public val ProtocolResponse: AttributeKey<ProtocolResponse> = AttributeKey("ProtocolResponse")
        public val RequestId: AttributeKey<String> = AttributeKey("RequestId")
    }

    /**
     * The name of the service that sent this error response
     */
    public val serviceName: String
        get() = attributes.getOrNull(SdkClientOption.ServiceName) ?: ""

    /**
     * Returns the error code associated with the response (if available).
     *
     * This value is implementation-defined. For example, AWS protocols use error code to identify the shape ID of the
     * error (see
     * [restJson1 protocol errors](https://awslabs.github.io/smithy/1.0/spec/aws/aws-restjson1-protocol.html#operation-error-serialization)
     * for details).
     */
    public val errorCode: String?
        get() = attributes.getOrNull(ErrorCode)

    /**
     * Indicates who is responsible for this exception (caller, service, or unknown)
     */
    public val errorType: ServiceException.ErrorType
        get() = attributes.getOrNull(ErrorType) ?: ServiceException.ErrorType.Unknown

    /**
     * The protocol response if available (this will differ depending on the underlying protocol e.g. HTTP, MQTT, etc)
     */
    public val protocolResponse: ProtocolResponse
        get() = attributes.getOrNull(ProtocolResponse) ?: EmptyProtocolResponse

    /**
     * The request ID that was returned by the called service.
     *
     * This value is implementation-defined. For example, AWS services trace and return unique request IDs for API
     * calls.
     */
    public val requestId: String?
        get() = attributes.getOrNull(RequestId)
}

/**
 * ServiceException - Base exception class for any error response returned by a service. Receiving an exception of this
 * type indicates that the caller's request was successfully transmitted to the service and the service sent back an
 * error response.
 */
public open class ServiceException : SdkBaseException {

    /**
     * Indicates who (if known) is at fault for this exception.
     */
    public enum class ErrorType {
        Client,
        Server,
        Unknown,
    }

    public constructor() : super()

    public constructor(message: String?) : super(message)

    public constructor(message: String?, cause: Throwable?) : super(message, cause)

    public constructor(cause: Throwable?) : super(cause)

    override val sdkErrorMetadata: ServiceErrorMetadata = ServiceErrorMetadata()
}
