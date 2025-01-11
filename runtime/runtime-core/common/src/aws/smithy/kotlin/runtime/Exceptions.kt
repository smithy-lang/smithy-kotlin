/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime

import aws.smithy.kotlin.runtime.collections.AttributeKey
import aws.smithy.kotlin.runtime.collections.MutableAttributes
import aws.smithy.kotlin.runtime.collections.mutableAttributes

/**
 * Describes additional context about an error which may be useful in client-side debugging. This information will be
 * included in exception messages. This contrasts with [ErrorMetadata] which is not _necessarily_ included in messages
 * and not _necessarily_ client-related.
 * @param key A header or key for the information
 * @param value A value for the information
 */
public class ClientErrorContext(public val key: String, public val value: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ClientErrorContext

        if (key != other.key) return false
        if (value != other.value) return false

        return true
    }

    /**
     * Gets a formatted representation of this error context suitable for inclusion in a message. This format is
     * generally `"$key: $value"`.
     */
    public val formatted: String = "$key: $value"

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }

    override fun toString(): String = "ClientErrorContext(key='$key', value='$value')"
}

/**
 * Additional metadata about an error
 */
public open class ErrorMetadata {
    @InternalApi
    public val attributes: MutableAttributes = mutableAttributes()

    public companion object {
        /**
         * Set if there are additional context elements about the error
         */
        public val AdditionalClientContext: AttributeKey<List<ClientErrorContext>> =
            AttributeKey("aws.smithy.kotlin#AdditionalClientContext")

        /**
         * Set if an error is retryable
         */
        public val Retryable: AttributeKey<Boolean> = AttributeKey("aws.smithy.kotlin#Retryable")

        /**
         * Set if an error represents a throttling condition
         */
        public val ThrottlingError: AttributeKey<Boolean> = AttributeKey("aws.smithy.kotlin#ThrottlingError")
    }

    public val isRetryable: Boolean
        get() = attributes.getOrNull(Retryable) ?: false

    public val isThrottling: Boolean
        get() = attributes.getOrNull(ThrottlingError) ?: false

    public val additionalClientContext: List<ClientErrorContext>
        get() = attributes.getOrNull(AdditionalClientContext).orEmpty()
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
public interface ProtocolResponse {
    /**
     * A short string summarizing the response, suitable for display in exception messages or debug scenarios
     */
    public val summary: String
}

private object EmptyProtocolResponse : ProtocolResponse {
    override val summary: String = "(empty response)"
}

public open class ServiceErrorMetadata : ErrorMetadata() {
    public companion object {
        public val ErrorCode: AttributeKey<String> = AttributeKey("aws.smithy.kotlin#ErrorCode")
        public val ErrorMessage: AttributeKey<String> = AttributeKey("aws.smithy.kotlin#ErrorMessage")
        public val ErrorType: AttributeKey<ServiceException.ErrorType> = AttributeKey("aws.smithy.kotlin#ErrorType")
        public val ProtocolResponse: AttributeKey<ProtocolResponse> = AttributeKey("aws.smithy.kotlin#ProtocolResponse")
        public val RequestId: AttributeKey<String> = AttributeKey("aws.smithy.kotlin#RequestId")
    }

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
     * Returns the human readable error message associated with the response. This value is implementation-defined.
     *
     * For example AWS protocol errors with a `message` field as part of the model this will match the `message`
     * property of the exception.
     */
    public val errorMessage: String?
        get() = attributes.getOrNull(ErrorMessage)

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

    private val displayMetadata: List<String>
        get() = buildList {
            val serviceProvidedMessage = super.message ?: sdkErrorMetadata.errorMessage
            if (serviceProvidedMessage == null) {
                sdkErrorMetadata.errorCode?.let { add("Service returned error code $it") }
                add("Error type: ${sdkErrorMetadata.errorType}")
                add("Protocol response: ${sdkErrorMetadata.protocolResponse.summary}")
            } else {
                add(serviceProvidedMessage)
            }

            sdkErrorMetadata.requestId?.let { add("Request ID: $it") }

            sdkErrorMetadata.additionalClientContext.mapTo(this@buildList) { it.formatted }
        }

    override val message: String
        get() = displayMetadata.joinToString()

    override val sdkErrorMetadata: ServiceErrorMetadata = ServiceErrorMetadata()
}
