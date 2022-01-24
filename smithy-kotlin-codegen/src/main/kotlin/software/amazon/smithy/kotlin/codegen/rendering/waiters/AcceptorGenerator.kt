/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.waiters

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.jmespath.JmespathExpression
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.addImport
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.utils.dq
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.waiters.*

/**
 * Renders an individual acceptor for a waiter.
 */
private fun KotlinWriter.renderAcceptor(wi: WaiterInfo, acceptor: Acceptor) {
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

        is Matcher.ErrorTypeMember -> renderErrorAcceptor(wi, directive, matcher)
        is Matcher.InputOutputMember -> renderPathAcceptor(wi, directive, true, matcher.value)
        is Matcher.OutputMember -> renderPathAcceptor(wi, directive, false, matcher.value)
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
        wi.waiter.acceptors.forEach { renderAcceptor(wi, it) }
    }
}

/**
 * Render an errorType acceptor.
 */
private fun KotlinWriter.renderErrorAcceptor(
    wi: WaiterInfo,
    directive: String,
    matcher: Matcher.ErrorTypeMember,
) {
    val errorShapeId = ShapeId.fromOptionalNamespace(wi.service.toShapeId().namespace, matcher.value)
    val errorShape = wi.ctx.model.getShape(errorShapeId).orElseThrow {
        CodegenException("Cannot find error type ${matcher.value} in model")
    }
    val errorSymbol = wi.ctx.symbolProvider.toSymbol(errorShape)

    addImport(
        RuntimeTypes.Core.Retries.Policy.ErrorTypeAcceptor,
        errorSymbol,
    )

    write("ErrorTypeAcceptor(RetryDirective.#L, #T::class),", directive, errorSymbol)
}

/**
 * Render a path-based acceptor (i.e., one that uses an output or inputOutput matcher).
 */
private fun KotlinWriter.renderPathAcceptor(
    wi: WaiterInfo,
    directive: String,
    includeInput: Boolean,
    matcher: PathMatcher,
) {
    val acceptorType = if (includeInput) {
        addImport(RuntimeTypes.Core.Retries.Policy.InputOutputAcceptor)
        "InputOutputAcceptor"
    } else {
        addImport(RuntimeTypes.Core.Retries.Policy.OutputAcceptor)
        "OutputAcceptor"
    }

    withBlock("#L(RetryDirective.#L) {", "},", acceptorType, directive) {
        val visitor = KotlinJmespathExpressionVisitor(
            includeInput,
            wi.ctx.model,
            wi.ctx.symbolProvider,
            wi.input,
            wi.inputSymbol,
            wi.output,
            wi.outputSymbol,
        )

        val expression = JmespathExpression.parse(matcher.path)
        expression.accept(visitor)

        val actual = visitor.renderActual(this)

        val expected = matcher.expected
        val comparison = when (matcher.comparator!!) {
            PathComparator.STRING_EQUALS -> "$actual?.toString() == ${expected.dq()}"
            PathComparator.BOOLEAN_EQUALS -> "$actual == ${expected.toBoolean()}"
            PathComparator.ANY_STRING_EQUALS -> "$actual?.any { it?.toString() == ${expected.dq()} } ?: false"
            PathComparator.ALL_STRING_EQUALS ->
                "($actual?.size ?: 0) > 1 && $actual?.all { it?.toString() == ${expected.dq()} }"
        }
        write(comparison)
    }
}
