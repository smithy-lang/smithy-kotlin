/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http.operation

import software.aws.clientrt.client.ExecutionContext
import software.aws.clientrt.http.request.HttpRequestBuilder

/**
 * Wrapper around an operation [input] type with an execution context.
 */
data class OperationRequest<T>(val context: ExecutionContext, val input: T)

/**
 * Wrapper around an outgoing HTTP request being built ([HttpRequestBuilder])
 * with an execution context.
 */
data class SdkHttpRequest(val context: ExecutionContext, val builder: HttpRequestBuilder)
