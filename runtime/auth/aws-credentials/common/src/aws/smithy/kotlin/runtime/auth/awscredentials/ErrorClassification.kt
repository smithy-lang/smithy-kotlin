/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awscredentials

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.SdkBaseException
import aws.smithy.kotlin.runtime.ServiceErrorMetadata
import aws.smithy.kotlin.runtime.ServiceException

/**
 * Walks this throwable and everything reachable from it via [Throwable.cause] and
 * [Throwable.suppressedExceptions], breadth-first, yielding each exception at most once.
 *
 * Both edges are required. `IdentityProviderChain.resolve` reports each provider's failure as a *suppressed*
 * exception on a single chain exception, while individual providers wrap their failures as *causes*. A classifier
 * that follows only one edge silently fails to classify anything resolved through the default chain.
 */
@InternalApi
public fun Throwable.causeChain(): Sequence<Throwable> = sequence {
    val seen = mutableSetOf<Throwable>()
    val queue = ArrayDeque<Throwable>()
    queue.add(this@causeChain)
    while (queue.isNotEmpty()) {
        val ex = queue.removeFirst()
        if (!seen.add(ex)) continue // cycle or diamond
        yield(ex)
        ex.cause?.let(queue::add)
        queue.addAll(ex.suppressedExceptions)
    }
}

/**
 * The service error code of the first [ServiceException] reachable from this throwable, if any.
 *
 * Exposed for the providers, which use it to recognize their own service's non-recoverable codes before they throw.
 */
@InternalApi
public fun Throwable.serviceErrorCode(): String? = causeChain()
    .filterIsInstance<ServiceException>()
    .firstNotNullOfOrNull { it.sdkErrorMetadata.attributes.getOrNull(ServiceErrorMetadata.ErrorCode) }

/**
 * Whether a credential resolution failure is non-recoverable: raise it immediately rather than applying static
 * stability, and cache it briefly.
 *
 * One classifier for every failure, carrying no service knowledge of its own. The provider that failed has already
 * answered the question by setting `ErrorMetadata.NonRecoverable`; all that is left is finding the exception that
 * carries the flag, which may be several wrappings down and may be a *suppressed* exception rather than a cause.
 */
@InternalApi
public fun Throwable.isNonRecoverableCredentialsError(): Boolean = causeChain().any { (it as? SdkBaseException)?.sdkErrorMetadata?.isNonRecoverable == true }
