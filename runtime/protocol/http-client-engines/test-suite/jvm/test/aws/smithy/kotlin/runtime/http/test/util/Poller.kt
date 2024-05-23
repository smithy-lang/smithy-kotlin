/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.test.util

import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration

/**
 * A utility class for polling a resource until some specific condition is met
 * @param maxWait The maximum amount of time the poller will wait for a condition to be met
 * @param interval The amount of time to delay between polls
 */
class Poller(val maxWait: Duration, val interval: Duration) {
    /**
     * Poll [resource] by executing [action] until it returns true
     */
    fun pollTrue(resource: String, action: () -> Boolean) {
        poll(resource, action) { it }
    }

    /**
     * Poll [resource] by executing [action] until it returns non-null
     */
    fun <T : Any> pollNotNull(resource: String, action: () -> T?): T = poll(resource, action) { it != null }!!

    /**
     * Poll [resource] by executing [action] until [condition] is met on the result
     */
    fun <T> poll(resource: String, action: () -> T, condition: (T) -> Boolean): T = runBlocking {
        val startTime = Instant.now()
        val stopTime = startTime + maxWait

        var result = action()
        var ready = condition(result)
        while (!ready && (Instant.now() + interval < stopTime)) {
            delay(interval)
            result = action()
            ready = condition(result)
        }

        check(ready) { "$resource not ready within $maxWait" }

        val elapsed = Instant.now() - startTime
        println("$resource is ready after $elapsed")

        result
    }
}
