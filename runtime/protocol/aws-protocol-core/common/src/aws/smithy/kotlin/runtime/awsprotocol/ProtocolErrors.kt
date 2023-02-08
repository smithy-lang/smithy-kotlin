/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.awsprotocol

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.ServiceErrorMetadata
import aws.smithy.kotlin.runtime.ServiceException
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.util.setIfValueNotNull

/**
 * Common error response details
 */
public interface AwsErrorDetails {
    /**
     * The error code that identifies the error
     */
    public val code: String?

    /**
     * A description of the error
     */
    public val message: String?

    /**
     * The unique request ID returned by the service
     */
    public val requestId: String?
}

@InternalApi
public data class ErrorDetails(
    override val code: String?,
    override val message: String?,
    override val requestId: String?,
) : AwsErrorDetails

/**
 * Pull specific details from the response / error and set [ServiceException] metadata
 */
@InternalApi
public fun setAseErrorMetadata(exception: Any, response: HttpResponse, errorDetails: AwsErrorDetails?) {
    if (exception is ServiceException) {
        exception.sdkErrorMetadata.attributes.setIfValueNotNull(ServiceErrorMetadata.ErrorCode, errorDetails?.code)
        exception.sdkErrorMetadata.attributes.setIfValueNotNull(ServiceErrorMetadata.ErrorMessage, errorDetails?.message)
        exception.sdkErrorMetadata.attributes.setIfValueNotNull(ServiceErrorMetadata.RequestId, response.headers[X_AMZN_REQUEST_ID_HEADER])
        exception.sdkErrorMetadata.attributes[ServiceErrorMetadata.ProtocolResponse] = response
    }
}
