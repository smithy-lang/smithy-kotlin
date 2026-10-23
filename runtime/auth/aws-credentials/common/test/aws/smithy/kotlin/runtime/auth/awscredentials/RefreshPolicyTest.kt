/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awscredentials

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Policy dispatch is the part most likely to regress silently: nothing fails to compile if a declaration stops being
 * honored, the cache just quietly applies the wrong behavior.
 */
class RefreshPolicyTest {
    @Test
    fun testEachBehaviorMapsToItsPolicy() {
        assertSame(
            RefreshPolicy.FullLifecycle,
            policyFor(CredentialsRefreshBehavior.RefreshableWithStaticStability),
        )
        assertSame(RefreshPolicy.Passthrough, policyFor(CredentialsRefreshBehavior.NonRefreshable))
        assertEquals(
            2,
            CredentialsRefreshBehavior.entries.size,
            "a new behavior needs a policy; this fails until policyFor maps it",
        )
    }

    @Test
    fun testNoDeclarationGetsTheFailSafe() {
        // a custom provider that declares nothing must not inherit static stability
        assertSame(RefreshPolicy.CachingOnly, policyFor(null))
        assertEquals(false, RefreshPolicy.CachingOnly.staticStability)
    }

    @Test
    fun testDispatchIsOnTheDeclarationNotTheProviderName() {
        val impostor = testCredentials(providerName = "IMDSv2")
        assertNull(impostor.refreshBehavior, "the debug name is not a declaration")
        assertSame(RefreshPolicy.CachingOnly, policyFor(impostor.refreshBehavior))
    }

    @Test
    fun testStaticStabilityIsAnExplicitOptIn() {
        // a custom provider that does declare it gets it: asked for on purpose, never inherited
        val custom = testCredentials(
            providerName = "MyProvider",
            behavior = CredentialsRefreshBehavior.RefreshableWithStaticStability,
        )
        assertEquals(CredentialsRefreshBehavior.RefreshableWithStaticStability, custom.refreshBehavior)
        assertTrue(policyFor(custom.refreshBehavior).staticStability)
    }

    @Test
    fun testPassthroughIsCachingOnlyPlusACadence() {
        assertEquals(false, RefreshPolicy.Passthrough.staticStability)
        assertEquals(1.minutes, RefreshPolicy.Passthrough.undatedCadence)
        assertEquals(true, RefreshPolicy.Passthrough.pacedRefresh)

        assertNull(RefreshPolicy.CachingOnly.undatedCadence)
        assertNull(RefreshPolicy.FullLifecycle.undatedCadence)
        assertEquals(false, RefreshPolicy.FullLifecycle.pacedRefresh, "a governed source paces on its own expiration")
    }

    @Test
    fun testStandardAdvisoryWindow() {
        assertEquals(5.minutes, standardAdvisoryWindow(1.minutes))
        assertEquals(5.minutes, standardAdvisoryWindow(20.minutes))
        assertEquals(15.minutes, standardAdvisoryWindow(20.minutes + 1.seconds))
        assertEquals(15.minutes, standardAdvisoryWindow(89.minutes))
        assertEquals(60.minutes, standardAdvisoryWindow(90.minutes))
        assertEquals(60.minutes, standardAdvisoryWindow(12.hours))
    }

    @Test
    fun testRefreshBehaviorIsReadFromTheCredentialsAttribute() {
        assertNull(testCredentials().refreshBehavior)
        assertEquals(
            CredentialsRefreshBehavior.NonRefreshable,
            testCredentials(behavior = CredentialsRefreshBehavior.NonRefreshable).refreshBehavior,
        )
    }
}
