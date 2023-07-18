/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.ErrorMetadata
import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.ServiceException
import aws.smithy.kotlin.runtime.client.ResponseInterceptorContext
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import kotlin.reflect.KClass

/**
 * This interceptor detects a discovered endpoint error and calls a specific [invalidate] lambda. Receiving such an
 * error indicates that a discovered endpoint is no longer valid (e.g., has expired) and the endpoint should be
 * re-discovered.
 * @param errorType The service-specific error type which indicates a discovered endpoint error.
 * @param invalidate A callback that takes action upon the receipt of a discovered endpoint error.
 */
@InternalApi
public class DiscoveredEndpointErrorInterceptor(
    private val errorType: KClass<out ServiceException>,
    private val invalidate: (ExecutionContext) -> Unit,
) : HttpInterceptor {
    override suspend fun modifyBeforeAttemptCompletion(
        context: ResponseInterceptorContext<Any, Any, HttpRequest, HttpResponse?>,
    ): Result<Any> {
        context.response.exceptionOrNull()?.let { e ->
            if (errorType.isInstance(e)) {
                invalidate(context.executionContext)
                (e as ServiceException).sdkErrorMetadata.attributes[ErrorMetadata.Retryable] = true
            }
        }

        return context.response
    }
}
