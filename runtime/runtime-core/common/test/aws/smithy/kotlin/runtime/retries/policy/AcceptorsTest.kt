/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.retries.policy

import aws.smithy.kotlin.runtime.ServiceErrorMetadata
import aws.smithy.kotlin.runtime.ServiceException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AcceptorsTest {
    @Test
    fun testSuccessAcceptor() {
        val normalResponse = Result.success(Unit)
        val errorResponse = Result.failure<Unit>(IllegalStateException())

        val successAcceptor = SuccessAcceptor(RetryDirective.TerminateAndSucceed, true)
        assertEquals(RetryDirective.TerminateAndSucceed, successAcceptor.evaluate(Unit, normalResponse))
        assertNull(successAcceptor.evaluate(Unit, errorResponse))

        val failureAcceptor = SuccessAcceptor(RetryDirective.TerminateAndFail, false)
        assertEquals(RetryDirective.TerminateAndFail, failureAcceptor.evaluate(Unit, errorResponse))
        assertNull(failureAcceptor.evaluate(Unit, normalResponse))
    }

    @Test
    fun testErrorTypeAcceptor_ByType() {
        val normalResponse = Result.success(Unit)
        val unexpectedException = Result.failure<Unit>(IllegalArgumentException())
        val expectedException = Result.failure<Unit>(IllegalStateException())

        val illegalStateAcceptor = ErrorTypeAcceptor(RetryDirective.TerminateAndSucceed, "IllegalStateException")
        assertEquals(RetryDirective.TerminateAndSucceed, illegalStateAcceptor.evaluate(Unit, expectedException))
        assertNull(illegalStateAcceptor.evaluate(Unit, unexpectedException))
        assertNull(illegalStateAcceptor.evaluate(Unit, normalResponse))
    }

    @Test
    fun testErrorTypeAcceptor_ByCode() {
        fun serviceException(code: String) = ServiceException().apply {
            sdkErrorMetadata.attributes[ServiceErrorMetadata.ErrorCode] = code
        }

        val normalResponse = Result.success(Unit)
        val unexpectedTypeException = Result.failure<Unit>(IllegalArgumentException())
        val unexpectedCodeException = Result.failure<Unit>(serviceException("BadArgument"))
        val expectedException = Result.failure<Unit>(serviceException("BadState"))

        val badStateAcceptor = ErrorTypeAcceptor(RetryDirective.TerminateAndSucceed, "BadState")
        assertEquals(RetryDirective.TerminateAndSucceed, badStateAcceptor.evaluate(Unit, expectedException))
        assertNull(badStateAcceptor.evaluate(Unit, unexpectedTypeException))
        assertNull(badStateAcceptor.evaluate(Unit, unexpectedCodeException))
        assertNull(badStateAcceptor.evaluate(Unit, normalResponse))
    }

    @Test
    fun testOutputAcceptor() {
        val normalResponse = Result.success("Hooray!")
        val unexpectedResponse = Result.success("!yarooH")
        val errorResponse = Result.failure<String>(IllegalStateException())

        val hoorayAcceptor = OutputAcceptor<String>(RetryDirective.TerminateAndSucceed) { it == "Hooray!" }
        assertEquals(RetryDirective.TerminateAndSucceed, hoorayAcceptor.evaluate(Unit, normalResponse))
        assertNull(hoorayAcceptor.evaluate(Unit, unexpectedResponse))
        assertNull(hoorayAcceptor.evaluate(Unit, errorResponse))
    }

    @Test
    fun testInputOutputAcceptor() {
        val normalRequest = "Whaddya say?"
        val unexpectedRequest = "?yas ayddahW"

        val normalResponse = Result.success("Hooray!")
        val unexpectedResponse = Result.success("!yarooH")
        val errorResponse = Result.failure<String>(IllegalStateException())

        val acceptor = InputOutputAcceptor<String, String>(RetryDirective.TerminateAndSucceed) {
            it.input == "Whaddya say?" && it.output == "Hooray!"
        }
        assertEquals(RetryDirective.TerminateAndSucceed, acceptor.evaluate(normalRequest, normalResponse))
        assertNull(acceptor.evaluate(unexpectedRequest, normalResponse))
        assertNull(acceptor.evaluate(normalRequest, unexpectedResponse))
        assertNull(acceptor.evaluate(unexpectedRequest, unexpectedResponse))
        assertNull(acceptor.evaluate(normalRequest, errorResponse))
        assertNull(acceptor.evaluate(unexpectedRequest, errorResponse))
    }
}
