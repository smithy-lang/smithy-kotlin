/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.waiters

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.model.hasAllOptionalMembers
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

/**
 * Renders the top-level retry strategy for a waiter.
 */
private fun KotlinWriter.renderRetryStrategy(wi: WaiterInfo, asValName: String) {
    withBlock("val #L = #T {", "}", asValName, RuntimeTypes.Core.Retries.StandardRetryStrategy) {
        write("maxAttempts = 20")
        write("tokenBucket = #T", RuntimeTypes.Core.Retries.Delay.InfiniteTokenBucket)
        withBlock("delayProvider {", "}") {
            write("initialDelay = #L.#T", wi.waiter.minDelay.msFormat, KotlinTypes.Time.milliseconds)
            write("scaleFactor = 1.5")
            write("jitter = 1.0")
            write("maxBackoff = #L.#T", wi.waiter.maxDelay.msFormat, KotlinTypes.Time.milliseconds)
        }
    }
}

/**
 * Renders the client extension methods for a waiter.
 */
internal fun KotlinWriter.renderWaiter(wi: WaiterInfo) {
    write("")
    wi.waiter.documentation.ifPresent(::dokka)
    val inputParameter = if (wi.input.hasAllOptionalMembers) {
        format("request: #1T = #1T{}", wi.inputSymbol)
    } else {
        format("request: #T", wi.inputSymbol)
    }
    withBlock(
        "#L suspend fun #T.#L(#L): #T<#T> {",
        "}",
        wi.ctx.settings.api.visibility,
        wi.serviceSymbol,
        wi.methodName,
        inputParameter,
        RuntimeTypes.Core.Retries.Outcome,
        wi.outputSymbol,
    ) {
        renderRetryStrategy(wi, "strategy")
        write("")
        renderAcceptorList(wi, "acceptors")
        write("")
        write("val policy = #T(request, acceptors)", RuntimeTypes.Core.Retries.Policy.AcceptorRetryPolicy)
        write("return strategy.retry(policy) { #L(request) }", wi.opMethodName)
    }

    write("")
    wi.waiter.documentation.ifPresent(this::dokka)
    write(
        "#L suspend fun #T.#L(block: #T.Builder.() -> Unit): #T<#T> =",
        wi.ctx.settings.api.visibility,
        wi.serviceSymbol,
        wi.methodName,
        wi.inputSymbol,
        RuntimeTypes.Core.Retries.Outcome,
        wi.outputSymbol,
    )
    indent()
    write("#L(#T.Builder().apply(block).build())", wi.methodName, wi.inputSymbol)
    dedent()
}

private val thousandsFormatter = DecimalFormat(",###", DecimalFormatSymbols().apply { groupingSeparator = '_' })

private val Int.msFormat: String
    get() = thousandsFormatter.format(this * 1000)
