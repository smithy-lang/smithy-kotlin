/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.io.internal.JobChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class JobChannelTest {
    @Test
    fun testClose() = runTest {
        val ch = JobChannel()
        val job = Job()
        ch.attachJob(job)
        assertTrue(job.isActive)
        ch.close()
        assertTrue(job.isActive)
    }

    @Test
    fun testCloseWithCause() = runTest {
        val ch = JobChannel()
        val job = Job()
        ch.attachJob(job)
        assertTrue(job.isActive)
        val cause = TestException()
        ch.close(cause)
        assertFalse(job.isActive)
        assertTrue(job.isCancelled)
    }

    @Test
    fun testCancel() = runTest {
        val ch = JobChannel()
        val job = Job()
        ch.attachJob(job)
        assertTrue(job.isActive)
        val cause = TestException()
        ch.cancel(cause)
        assertFalse(job.isActive)
        assertTrue(job.isCancelled)
    }
}
