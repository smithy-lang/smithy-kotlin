/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.waiters

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.addImport
import software.amazon.smithy.kotlin.codegen.core.withBlock
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

/**
 * Renders the top-level retry strategy for a waiter.
 */
private fun KotlinWriter.renderRetryStrategy(wi: WaiterInfo, asValName: String) {
    addImport(
        RuntimeTypes.Core.Retries.Delay.ExponentialBackoffWithJitterOptions,
        RuntimeTypes.Core.Retries.Delay.ExponentialBackoffWithJitter,
        RuntimeTypes.Core.Retries.StandardRetryStrategyOptions,
        RuntimeTypes.Core.Retries.StandardRetryStrategy,
        RuntimeTypes.Core.Retries.Delay.InfiniteTokenBucket,
    )

    withBlock("val #L = run {", "}", asValName) {
        withBlock("val delayOptions = ExponentialBackoffWithJitterOptions(", ")") {
            write("initialDelayMs = #L,", wi.waiter.minDelay.msFormat)
            write("scaleFactor = 1.5,")
            write("jitter = 1.0,")
            write("maxBackoffMs = #L,", wi.waiter.maxDelay.msFormat)
        }
        write("val delay = ExponentialBackoffWithJitter(delayOptions)")
        write("")
        write("val waiterOptions = StandardRetryStrategyOptions(maxTimeMs = 300_000, maxAttempts = 20)")
        write("StandardRetryStrategy(waiterOptions, InfiniteTokenBucket, delay)")
    }
}

/**
 * Renders the client extension methods for a waiter.
 */
internal fun KotlinWriter.renderWaiter(wi: WaiterInfo) {
    addImport(
        wi.serviceSymbol,
        wi.inputSymbol,
        wi.outputSymbol,
        RuntimeTypes.Core.Retries.Outcome,
        RuntimeTypes.Core.Retries.Delay.ExponentialBackoffWithJitterOptions,
        RuntimeTypes.Core.Retries.Delay.ExponentialBackoffWithJitter,
        RuntimeTypes.Core.Retries.StandardRetryStrategyOptions,
        RuntimeTypes.Core.Retries.StandardRetryStrategy,
        RuntimeTypes.Core.Retries.Policy.RetryDirective,
        RuntimeTypes.Core.Retries.Policy.AcceptorRetryPolicy,
    )

    write("")
    wi.waiter.documentation.ifPresent(::dokka)
    withBlock(
        "suspend fun #T.#L(request: #T): Outcome<#T> {",
        "}",
        wi.serviceSymbol,
        wi.methodName,
        wi.inputSymbol,
        wi.outputSymbol,
    ) {
        renderRetryStrategy(wi, "strategy")
        write("")
        renderAcceptorList(wi, "acceptors")
        write("")
        write("val policy = AcceptorRetryPolicy(request, acceptors)")
        write("return strategy.retry(policy) { #L(request) }", wi.opMethodName)
    }

    write("")
    wi.waiter.documentation.ifPresent(this::dokka)
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
