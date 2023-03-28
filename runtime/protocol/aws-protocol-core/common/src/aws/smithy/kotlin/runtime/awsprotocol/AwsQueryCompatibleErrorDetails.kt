/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.awsprotocol

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.ServiceErrorMetadata
import aws.smithy.kotlin.runtime.ServiceException

/**
 * Header name for awsQuery-compatible error values.
 */
@InternalApi
public const val X_AMZN_QUERY_ERROR_HEADER: String = "x-amzn-query-error"

/**
 * Error details presented for backwards-compatibility by services that have migrated from awsQuery.
 */
@InternalApi
public data class AwsQueryCompatibleErrorDetails(
    public val code: String,
    public val type: ServiceException.ErrorType,
) {
    public companion object {
        public fun parse(value: String): AwsQueryCompatibleErrorDetails = parseImpl(value)
    }
}

/**
 * Set awsQuery error details on a [ServiceException]
 */
@InternalApi
public fun setAwsQueryCompatibleErrorMetadata(exception: Any, error: AwsQueryCompatibleErrorDetails) {
    if (exception is ServiceException) {
        exception.sdkErrorMetadata.attributes[ServiceErrorMetadata.ErrorCode] = error.code
        exception.sdkErrorMetadata.attributes[ServiceErrorMetadata.ErrorType] = error.type
    }
}

// parse an awsQuery error from its string representation
// the value is formatted as `code;type` e.g. `AWS.SimpleQueueService.NonExistentQueue;Sender`.
private fun parseImpl(error: String): AwsQueryCompatibleErrorDetails {
    val segments = error.split(";", limit = 2)
    require(segments.size == 2) { "value is malformed" }
    require(segments[0].isNotEmpty()) { "code is empty" }
    require(segments[1].isNotEmpty()) { "type is empty" }

    val code = segments[0]
    val type = when (segments[1]) { // can be empty
        "Sender" -> ServiceException.ErrorType.Client
        "Receiver" -> ServiceException.ErrorType.Server
        else -> ServiceException.ErrorType.Unknown
    }
    return AwsQueryCompatibleErrorDetails(code, type)
}
