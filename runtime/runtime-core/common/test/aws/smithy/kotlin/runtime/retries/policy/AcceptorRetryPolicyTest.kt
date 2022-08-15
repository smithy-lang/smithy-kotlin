/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries.policy

import kotlin.test.Test
import kotlin.test.assertEquals

class AcceptorRetryPolicyTest {
    @Test
    fun testChain() {
        val acceptors = listOf<Acceptor<Unit, Unit>>(
            object : Acceptor<Unit, Unit>(RetryDirective.TerminateAndSucceed) {
                override fun matches(request: Unit, result: Result<Unit>): Boolean = false
            },
            object : Acceptor<Unit, Unit>(RetryDirective.RetryError(RetryErrorType.ServerSide)) {
                override fun matches(request: Unit, result: Result<Unit>): Boolean = false
            },
            object : Acceptor<Unit, Unit>(RetryDirective.TerminateAndFail) {
                override fun matches(request: Unit, result: Result<Unit>): Boolean = true
            },
        )
        val policy = AcceptorRetryPolicy(Unit, acceptors)
        assertEquals(RetryDirective.TerminateAndFail, policy.evaluate(Result.success(Unit)))
    }

    @Test
    fun testDefaultEvaluations() {
        val acceptors = listOf<Acceptor<Unit, Unit>>(
            object : Acceptor<Unit, Unit>(RetryDirective.TerminateAndSucceed) {
                override fun matches(request: Unit, result: Result<Unit>): Boolean = false
            },
        )
        val policy = AcceptorRetryPolicy(Unit, acceptors)
        assertEquals(RetryDirective.RetryError(RetryErrorType.ServerSide), policy.evaluate(Result.success(Unit)))
        assertEquals(RetryDirective.TerminateAndFail, policy.evaluate(Result.failure(IllegalStateException())))
    }
}
