/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io.internal

import aws.smithy.kotlin.runtime.io.SdkByteChannel
import aws.smithy.kotlin.runtime.util.InternalApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

/**
 * Ties the lifetime of the channel and a Job together. Channel failures (closed with exception) will
 * cause the underlying [Job] to fail with the channel exception.
 *
 * This is useful for launching coroutines with the soul purpose of reading/writing data to the channel where
 * the coroutine should only continue if the channel hasn't failed.
 */
@InternalApi
public class JobChannel(
    private val delegate: SdkByteChannel = SdkByteChannel(true),
) : SdkByteChannel by delegate {
    internal var job: Job? = null

    public fun attachJob(job: Job) {
        if (isClosedForRead) {
            job.cancel(CancellationException("channel was already closed", delegate.closedCause))
            return
        }
        this.job = job
    }

    override fun cancel(cause: Throwable?): Boolean {
        job?.cancel(CancellationException("channel was cancelled", cause))
        return delegate.cancel(cause)
    }

    override fun close(cause: Throwable?): Boolean {
        if (cause != null) {
            job?.cancel(CancellationException("channel was closed with cause", cause))
        }
        return delegate.close(cause)
    }

    override fun close() {
        delegate.close()
    }
}
