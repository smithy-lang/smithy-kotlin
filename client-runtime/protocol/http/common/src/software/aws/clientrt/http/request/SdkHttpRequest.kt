/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http.request

import software.aws.clientrt.client.ExecutionContext

/**
 * Wrapper around an operation [input] type with an execution context.
 */
data class OperationRequest<T>(val context: ExecutionContext, val input: T)

/**
 * An outgoing HTTP request being built
 */
data class SdkHttpRequest(val context: ExecutionContext, val request: HttpRequestBuilder)
