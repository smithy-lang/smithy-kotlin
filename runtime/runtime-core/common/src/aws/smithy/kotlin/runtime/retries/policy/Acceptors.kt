/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.retries.policy

import aws.smithy.kotlin.runtime.ServiceException
import kotlin.reflect.KClass

/**
 * An object that evaluates an operation request and result to determine if a specific condition is matched.
 * @param I The type of the input to the operation.
 * @param O The type of the output from the operation.
 * @param state The [RetryDirective] that applies when the acceptor's condition is matched.
 */
abstract class Acceptor<in I, in O>(val state: RetryDirective) {
    /**
     * Evaluates an operation request and result.
     * @param request The input to the operation.
     * @param result The output of the operation (either a regular response or an exception).
     * @return If this acceptor's condition is matched, then the acceptor's [state] is returned. Otherwise, null.
     */
    fun evaluate(request: I, result: Result<O>): RetryDirective? =
        if (matches(request, result)) state else null

    /**
     * Determines if this acceptor's condition is matched.
     * @param request The input to the operation.
     * @param result The output of the operation (either a regular response or an exception).
     * @return A boolean indicating if the acceptor's condition is matched.
     */
    protected abstract fun matches(request: I, result: Result<O>): Boolean
}

/**
 * An [Acceptor] that matches based on a response's success or failure.
 * @param state The [RetryDirective] that applies when the acceptor's condition is matched.
 * @param success If true, matches when the response is non-exceptional. Otherwise, matches when the response is
 * exceptional.
 */
class SuccessAcceptor(state: RetryDirective, val success: Boolean) : Acceptor<Any, Any>(state) {
    override fun matches(request: Any, result: Result<Any>): Boolean =
        result.isSuccess == success
}

/**
 * An [Acceptor] that matches based on a specific error type.
 * @param state The [RetryDirective] that applies when the acceptor's condition is matched.
 * @param errorType The [KClass] of error for this acceptor.
 */
class ErrorTypeAcceptor(state: RetryDirective, val errorType: String) : Acceptor<Any, Any>(state) {
    override fun matches(request: Any, result: Result<Any>): Boolean =
        (result.exceptionOrNull() as? ServiceException)?.sdkErrorMetadata?.errorCode == errorType
}

/**
 * An [Acceptor] that delegates to an output matcher function.
 * @param state The [RetryDirective] that applies when the acceptor's condition is matched.
 * @param matcher The logic for determining if this acceptor's condition is matched based on the operation's output.
 */
class OutputAcceptor<O>(state: RetryDirective, val matcher: (O) -> Boolean) : Acceptor<Any, O>(state) {
    override fun matches(request: Any, result: Result<O>): Boolean =
        result.getOrNull()?.run(matcher) ?: false
}

/**
 * An [Acceptor] that delegates to an input/output matcher function.
 * @param state The [RetryDirective] that applies when the acceptor's condition is matched.
 * @param matcher The logic for determining if this acceptor's condition is matched based on the operation's input and
 * output.
 */
class InputOutputAcceptor<I, O>(
    state: RetryDirective,
    val matcher: (InputOutput<I, O>) -> Boolean,
) : Acceptor<I, O>(state) {
    /**
     * A utility class holding the input and output to an operation.
     */
    data class InputOutput<I, O>(val input: I, val output: O)

    override fun matches(request: I, result: Result<O>): Boolean =
        result.getOrNull()?.let { matcher(InputOutput(request, it)) } ?: false
}
