/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awscredentials

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.ErrorMetadata
import aws.smithy.kotlin.runtime.SdkBaseException
import aws.smithy.kotlin.runtime.ServiceErrorMetadata
import aws.smithy.kotlin.runtime.ServiceException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The classifier carries no service knowledge of its own — the provider that failed sets the flag. What is tested
 * here is that the flag is *found*, however deeply it is buried and whichever edge it is buried behind.
 */
class ErrorClassificationTest {
    private fun flagged(message: String): SdkBaseException = ClientException(message).apply { sdkErrorMetadata.attributes[ErrorMetadata.NonRecoverable] = true }

    private fun serviceError(code: String): ServiceException = ServiceException("service said no").apply {
        sdkErrorMetadata.attributes[ServiceErrorMetadata.ErrorCode] = code
    }

    @Test
    fun testFlagFoundAtDepthOne() {
        assertTrue(flagged("boom").isNonRecoverableCredentialsError())
    }

    @Test
    fun testFlagFoundViaCause() {
        val wrapped = CredentialsProviderException("failed to resolve", flagged("expired session"))
        assertTrue(wrapped.isNonRecoverableCredentialsError())
    }

    @Test
    fun testFlagFoundViaSuppressed() {
        // this is the shape IdentityProviderChain produces: one chain exception, each provider's failure suppressed
        val chainFailure = CredentialsProviderException("no credentials could be resolved from the chain").apply {
            addSuppressed(RuntimeException("environment: not set"))
            addSuppressed(flagged("sso: session expired"))
        }
        assertTrue(chainFailure.isNonRecoverableCredentialsError())
    }

    @Test
    fun testFlagFoundViaSuppressedThenCause() {
        val chainFailure = CredentialsProviderException("chain failed").apply {
            addSuppressed(CredentialsProviderException("sso failed", flagged("session expired")))
        }
        assertTrue(chainFailure.isNonRecoverableCredentialsError())
    }

    @Test
    fun testUnflaggedIsRecoverable() {
        val wrapped = CredentialsProviderException("connection reset", RuntimeException("i/o"))
        assertFalse(wrapped.isNonRecoverableCredentialsError())
    }

    @Test
    fun testRetryableAndThrottlingAreNotNonRecoverable() {
        val throttled = ClientException("slow down").apply {
            sdkErrorMetadata.attributes[ErrorMetadata.Retryable] = true
            sdkErrorMetadata.attributes[ErrorMetadata.ThrottlingError] = true
        }
        assertFalse(throttled.isNonRecoverableCredentialsError())
    }

    @Test
    fun testCauseChainTerminatesOnASelfReferentialCause() {
        val ex = SelfCausedException()
        assertEquals(1, ex.causeChain().count())
    }

    @Test
    fun testCauseChainTerminatesOnADiamond() {
        val leaf = RuntimeException("leaf")
        val left = RuntimeException("left", leaf)
        val right = RuntimeException("right", leaf)
        val top = RuntimeException("top", left).apply { addSuppressed(right) }

        // leaf is reachable twice but yielded once
        assertEquals(4, top.causeChain().count())
        assertEquals(1, top.causeChain().count { it === leaf })
    }

    @Test
    fun testCauseChainIsBreadthFirst() {
        val deep = RuntimeException("deep")
        val top = RuntimeException("top", RuntimeException("mid", deep)).apply {
            addSuppressed(RuntimeException("sibling"))
        }
        assertEquals(
            listOf("top", "mid", "sibling", "deep"),
            top.causeChain().map { it.message }.toList(),
        )
    }

    @Test
    fun testServiceErrorCodeFindsTheFirstServiceException() {
        val wrapped = CredentialsProviderException("assume role failed", serviceError("RegionDisabledException"))
        assertEquals("RegionDisabledException", wrapped.serviceErrorCode())
    }

    @Test
    fun testServiceErrorCodeIsNullWithoutOne() {
        assertNull(CredentialsProviderException("i/o", RuntimeException()).serviceErrorCode())
        assertNull(ServiceException("no code set").serviceErrorCode())
    }

    /** A cause pointing at itself. Constructed here because `initCause` is not available on all targets. */
    private class SelfCausedException : RuntimeException("self") {
        override val cause: Throwable
            get() = this
    }
}
