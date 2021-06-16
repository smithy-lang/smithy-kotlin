/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http.operation

import software.aws.clientrt.client.ExecutionContext

/**
 * Wrapper around a type [subject] with an execution context.
 *
 * This type is typically used as the input for a [software.aws.clientrt.io.middleware.Phase] where [subject]
 * is the thing currently being worked on (built/serialized/etc).
 *
 * @param context The operation context
 * @param subject The input type
 */
data class OperationRequest<T>(val context: ExecutionContext, val subject: T)
