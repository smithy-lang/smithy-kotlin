/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.ErrorMetadata
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

internal sealed interface TimeoutScope {
    val description: String

    data object Call : TimeoutScope {
        override val description = "Call"
    }

    data class Attempt(val attempt: Int) : TimeoutScope {
        override val description = "Attempt #$attempt"
    }
}

/**
 * Indicates that a client-side configured timeout was exceeded (e.g., call timeout, attempt timeout, etc.)
 */
public abstract class ClientTimeoutException(
    message: String,
    cause: Throwable,
    retryable: Boolean,
) : ClientException(message, cause) {
    init {
        sdkErrorMetadata.attributes[ErrorMetadata.Retryable] = retryable
    }
}

/**
 * Indicates that a single attempt took longer than allowed to complete
 */
public class AttemptTimeoutException(message: String, cause: Throwable) : ClientTimeoutException(message, cause, true)

/**
 * Indicates that a call (including any retry attempts) took longer than allowed to complete
 */
public class CallTimeoutException(message: String, cause: Throwable) : ClientTimeoutException(message, cause, false)

internal suspend inline fun <T> scopedTimeout(
    scope: TimeoutScope,
    duration: Duration?,
    crossinline block: suspend () -> T,
): T = when (duration) {
    null -> block()
    else ->
        try {
            withTimeout(duration) { block() }
        } catch (e: TimeoutCancellationException) {
            val message = buildString {
                append(scope.description)
                append(" exceeded configured timeout of ")
                append(duration)
            }

            val exceptionType: (String, Throwable) -> Throwable = when (scope) {
                is TimeoutScope.Attempt -> ::AttemptTimeoutException
                TimeoutScope.Call -> ::CallTimeoutException
            }

            throw exceptionType(message, e)
        }
}
