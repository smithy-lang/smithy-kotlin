/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.InternalApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Dedupe multiple calls for a resource. A new [SingleFlightGroup] should be created
 * for each async call that requires debouncing.
 */
@InternalApi
public class SingleFlightGroup<T> {
    private val mu = Mutex()
    private var inFlight: CompletableDeferred<Result<T>>? = null
    private var waitCount = 0

    /**
     * Executes [block] on the first invocation if there are no in-flight calls already made waiting
     * for the response. If a call is already in-flight then waits for the response without kicking off
     * a new call to [block]
     * @param block the function to execute
     * @return the response produced or throws an exception
     */
    public suspend fun singleFlight(block: suspend () -> T): T {
        mu.lock()
        val job = inFlight
        if (job != null) {
            waitCount++
            mu.unlock()

            // wait for value to be produced
            job.join()

            mu.withLock {
                waitCount--
                if (waitCount == 0) {
                    // last one out - clear the current job
                    inFlight = null
                }
            }
            return job.await().getOrThrow()
        }

        // no active jobs, create a new in-flight request
        val deferred = CompletableDeferred<Result<T>>()
        inFlight = deferred
        mu.unlock()
        runCatching { block() }.let { deferred.complete(it) }
        return deferred.await().getOrThrow()
    }
}
