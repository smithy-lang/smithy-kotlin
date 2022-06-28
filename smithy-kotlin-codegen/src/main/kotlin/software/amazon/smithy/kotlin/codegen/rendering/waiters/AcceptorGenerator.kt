/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.waiters

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.jmespath.JmespathExpression
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.utils.dq
import software.amazon.smithy.waiters.*

/**
 * Renders an individual acceptor for a waiter.
 */
private fun KotlinWriter.renderAcceptor(acceptor: Acceptor) {
    addImport(RuntimeTypes.Core.Retries.Policy.RetryDirective)

    val directive = when (acceptor.state!!) {
        AcceptorState.SUCCESS -> "TerminateAndSucceed"
        AcceptorState.FAILURE -> "TerminateAndFail"
        AcceptorState.RETRY -> {
            addImport(RuntimeTypes.Core.Retries.Policy.RetryErrorType)
            "RetryError(RetryErrorType.ServerSide)"
        }
    }

    when (val matcher = acceptor.matcher) {
        is Matcher.SuccessMember -> {
            addImport(RuntimeTypes.Core.Retries.Policy.SuccessAcceptor)
            write("SuccessAcceptor(RetryDirective.#L, #L),", directive, matcher.value)
        }

        is Matcher.ErrorTypeMember -> {
            addImport(RuntimeTypes.Core.Retries.Policy.ErrorTypeAcceptor)
            write("ErrorTypeAcceptor(RetryDirective.#L, #L),", directive, matcher.value.dq())
        }

        is Matcher.InputOutputMember -> renderPathAcceptor(directive, true, matcher.value)
        is Matcher.OutputMember -> renderPathAcceptor(directive, false, matcher.value)
        else -> throw CodegenException("""Unknown matcher type "${matcher::class}"""")
    }
}

/**
 * Render the top-level list of acceptors for a waiter.
 */
internal fun KotlinWriter.renderAcceptorList(wi: WaiterInfo, asValName: String) {
    addImport(RuntimeTypes.Core.Retries.Policy.Acceptor)

    withBlock(
        "val #L = listOf<Acceptor<#T, #T>>(", ")",
        asValName,
        wi.inputSymbol,
        wi.outputSymbol,
    ) {
        wi.waiter.acceptors.forEach(::renderAcceptor)
    }
}

/**
 * Render a path-based acceptor (i.e., one that uses an output or inputOutput matcher).
 */
private fun KotlinWriter.renderPathAcceptor(directive: String, includeInput: Boolean, matcher: PathMatcher) {
    val acceptorType = if (includeInput) {
        addImport(RuntimeTypes.Core.Retries.Policy.InputOutputAcceptor)
        "InputOutputAcceptor"
    } else {
        addImport(RuntimeTypes.Core.Retries.Policy.OutputAcceptor)
        "OutputAcceptor"
    }

    withBlock("#L(RetryDirective.#L) {", "},", acceptorType, directive) {
        val visitor = KotlinJmespathExpressionVisitor(this)
        val expression = JmespathExpression.parse(matcher.path)
        val actual = expression.accept(visitor)

        val expected = matcher.expected
        val comparison = when (matcher.comparator!!) {
            PathComparator.STRING_EQUALS -> "$actual?.toString() == ${expected.dq()}"
            PathComparator.BOOLEAN_EQUALS -> "$actual == ${expected.toBoolean()}"
            PathComparator.ANY_STRING_EQUALS -> "$actual?.any { it?.toString() == ${expected.dq()} } ?: false"

            // NOTE: the size > 0 check is necessary because the waiter spec says that `allStringEquals` requires
            // at least one value unlike Kotlin's `all` which returns true if the collection is empty
            PathComparator.ALL_STRING_EQUALS ->
                "$actual != null && $actual.size > 0 && $actual.all { it?.toString() == ${expected.dq()} }"
        }
        write(comparison)
    }
}
