/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics.aggregation.internal

import aws.smithy.kotlin.runtime.io.Closeable
import aws.smithy.kotlin.runtime.io.SdkManaged
import aws.smithy.kotlin.runtime.telemetry.TelemetryProvider
import aws.smithy.kotlin.runtime.telemetry.context.ContextManager
import aws.smithy.kotlin.runtime.telemetry.logging.LoggerProvider
import aws.smithy.kotlin.runtime.telemetry.metrics.MeterProvider
import aws.smithy.kotlin.runtime.telemetry.trace.TracerProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Reference counting for providers the SDK creates on the caller's behalf.
 */
class ManagedTelemetryProviderTest {
    private class CloseableProvider :
        TelemetryProvider,
        Closeable {
        var closeCount = 0
            private set

        override val meterProvider: MeterProvider = MeterProvider.None
        override val tracerProvider: TracerProvider = TracerProvider.None
        override val loggerProvider: LoggerProvider = LoggerProvider.None
        override val contextManager: ContextManager = ContextManager.None

        override fun close() {
            closeCount++
        }
    }

    /**
     * A provider that holds nothing is returned untouched.
     *
     * Wrapping it would add a share count that never releases anything, and — more to the point — would make
     * `addIfManaged` start tracking a resource with no lifecycle, so client close would begin taking a lock
     * and mutating state for `TelemetryProvider.None`.
     */
    @Test
    fun testNonCloseableProviderIsNotWrapped() {
        assertSame(TelemetryProvider.None, TelemetryProvider.None.manage())
    }

    /**
     * A closeable provider is wrapped, and the wrapper forwards every telemetry member to the delegate.
     *
     * Forwarding is what makes wrapping invisible: a wrapper that returned its own `None` members would
     * silently disable telemetry for every client that received it.
     */
    @Test
    fun testCloseableProviderIsWrappedAndForwards() {
        val delegate = CloseableProvider()
        val managed = delegate.manage()

        assertIs<SdkManaged>(managed)
        assertSame(delegate.meterProvider, managed.meterProvider)
        assertSame(delegate.tracerProvider, managed.tracerProvider)
        assertSame(delegate.loggerProvider, managed.loggerProvider)
        assertSame(delegate.contextManager, managed.contextManager)
    }

    /** Wrapping twice returns the same wrapper, so there is only ever one share count per provider. */
    @Test
    fun testManageIsIdempotent() {
        val managed = CloseableProvider().manage()
        assertSame(managed, managed.manage())
    }

    /**
     * The delegate is closed only when the last user releases it.
     *
     * This is the property that makes one provider safe to hand to several clients: closing the first client
     * must not stop metrics for the others.
     */
    @Test
    fun testDelegateClosesOnlyAfterLastUnshare() {
        val delegate = CloseableProvider()
        val managed = delegate.manage() as SdkManaged

        managed.share()
        managed.share()

        assertFalse(managed.unshare())
        assertEquals(0, delegate.closeCount)

        assertTrue(managed.unshare())
        assertEquals(1, delegate.closeCount)
    }
}
