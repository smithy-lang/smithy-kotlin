/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.util.CanDeepCopy

/**
 * Wrapper around a type [subject] with an execution context.
 *
 * This type is typically used as the input for a [aws.smithy.kotlin.runtime.io.middleware.Phase] where [subject]
 * is the thing currently being worked on (built/serialized/etc).
 *
 * @param context The operation context
 * @param subject The input type
 */
public data class OperationRequest<T>(public val context: ExecutionContext, public val subject: T) {
    public constructor(subject: T) : this(ExecutionContext(), subject)
}

/**
 * Deep copy an [OperationRequest] to a new request. Note that, because [context] is...well, context...it's considered
 * transient and is not part of the copy. The subject itself, however, is deeply copied.
 * @return A new [OperationRequest] with the same context and a deeply-copied subject.
 */
internal fun <T : CanDeepCopy<T>> OperationRequest<T>.deepCopy(): OperationRequest<T> =
    OperationRequest(context, subject.deepCopy())
