/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.waiters

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

/**
 * Renders the top-level retry strategy for a waiter.
 */
private fun KotlinWriter.renderRetryStrategy(wi: WaiterInfo) {
    setOf(
        RuntimeTypes.Core.Retries.Impl.ExponentialBackoffWithJitterOptions,
        RuntimeTypes.Core.Retries.Impl.ExponentialBackoffWithJitter,
        RuntimeTypes.Core.Retries.Impl.StandardRetryStrategyOptions,
        RuntimeTypes.Core.Retries.Impl.StandardRetryStrategy,
        RuntimeTypes.Core.Retries.Impl.NoOpTokenBucket,
    ).forEach(::addImport)

    write("")
    withBlock("private val #L = run {", "}", wi.retryStrategyName) {
        withBlock("val delayOptions = ExponentialBackoffWithJitterOptions(", ")") {
            write("initialDelayMs = #L,", wi.waiter.minDelay.msFormat)
            write("scaleFactor = 1.5,")
            write("jitter = 1.0,")
            write("maxBackoffMs = #L,", wi.waiter.maxDelay.msFormat)
        }
        write("val delay = ExponentialBackoffWithJitter(delayOptions)")
        write("")
        write("val waiterOptions = StandardRetryStrategyOptions(maxTimeMs = 300_000, maxAttempts = 20)")
        write("StandardRetryStrategy(waiterOptions, NoOpTokenBucket, delay)")
    }
}

/**
 * Renders the client extension methods for a waiter.
 */
fun KotlinWriter.renderWaiter(wi: WaiterInfo) {
    renderRetryStrategy(wi)
    renderAcceptorList(wi)

    setOf(
        wi.serviceSymbol,
        wi.inputSymbol,
        wi.outputSymbol,
        RuntimeTypes.Core.Retries.Outcome,
        RuntimeTypes.Core.Retries.Impl.ExponentialBackoffWithJitterOptions,
        RuntimeTypes.Core.Retries.Impl.ExponentialBackoffWithJitter,
        RuntimeTypes.Core.Retries.Impl.StandardRetryStrategyOptions,
        RuntimeTypes.Core.Retries.Impl.StandardRetryStrategy,
        RuntimeTypes.Core.Retries.RetryDirective,
        RuntimeTypes.Core.Retries.Impl.Waiters.AcceptorRetryPolicy,
    ).forEach(::addImport)

    write("")
    withBlock(
        "suspend fun #T.#L(request: #T): Outcome<#T> {",
        "}",
        wi.serviceSymbol,
        wi.methodName,
        wi.inputSymbol,
        wi.outputSymbol,
    ) {
        write("val policy = AcceptorRetryPolicy(request, #L)", wi.acceptorListName)
        write("return #L.retry(policy) { #L(request) }", wi.retryStrategyName, wi.opMethodName)
    }

    write("")
    write(
        "suspend fun #T.#L(block: #T.Builder.() -> Unit): Outcome<#T> =",
        wi.serviceSymbol,
        wi.methodName,
        wi.inputSymbol,
        wi.outputSymbol,
    )
    indent()
    write("#L(#T.Builder().apply(block).build())", wi.methodName, wi.inputSymbol)
    dedent()
}

private val thousandsFormatter = DecimalFormat(",###", DecimalFormatSymbols().apply { groupingSeparator = '_' })

private val Int.msFormat: String
    get() = thousandsFormatter.format(this * 1000)
