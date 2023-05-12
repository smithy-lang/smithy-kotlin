/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SingleFlightGroupTest {
    private class TestResult

    @Test
    fun testDebounce() = runTest {
        val group = SingleFlightGroup<TestResult>()
        val mu = Mutex(locked = true)
        val deferreds = mutableListOf<Deferred<TestResult>>()

        repeat(3) {
            // Each of these singleFlight invocations should re-use the result of the "first" invocation
            //
            // NOTE: `runTest` runs on single thread and will queue these until a suspension point is reached,
            // and it looks for more tasks to run.
            val outN = async {
                group.singleFlight {
                    error("should not reach here")
                }
            }
            deferreds.add(outN)
        }

        // run up to withLock
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            group.singleFlight {
                // wait to queue up multiple results, this SHOULD be the first suspension point for the test
                mu.withLock {
                    TestResult()
                }
            }
        }

        // ensure all the other async tasks are scheduled and run to suspension points
        advanceUntilIdle()

        // ensure our tasks are appropriately queued and waiting
        assertEquals(3, deferreds.size)

        // allow the actual producer to proceed
        mu.unlock()

        val actual1 = first.await()
        val results = deferreds.awaitAll()

        // should all be exact same object instance
        results.forEach {
            assertSame(actual1, it)
        }

        // new invocation with zero waiters should result in a new execution
        val actual2 = group.singleFlight { TestResult() }
        assertNotSame(actual1, actual2)
    }

    @Test
    fun testDebounceErrors() = runTest {
        // since we are throwing an exception AND we share the same parent coroutineContext wrap in supervisor
        // scope to avoid cancelling the other inflight deferred
        supervisorScope {
            val group = SingleFlightGroup<TestResult>()
            val mu = Mutex(locked = true)

            val waiter = async {
                group.singleFlight { TestResult() }
            }

            val first = async(start = CoroutineStart.UNDISPATCHED) {
                group.singleFlight {
                    mu.withLock {
                        throw RuntimeException("producer error")
                    }
                }
            }

            advanceUntilIdle()
            mu.unlock()

            val ex1 = assertFails {
                first.await()
            }

            val ex2 = assertFails {
                waiter.await()
            }

            if (ex1.cause != null) {
                // we would expect the exceptions to be the same instance but kotlinx.coroutines will sometimes
                // attempt to copy the exception with a recovered stack trace more appropriate for the coroutine
                // and set the cause to the original unmodified exception.
                // https://github.com/Kotlin/kotlinx.coroutines/blob/c8ef9ec95e32b204de1c654d1c00d6674d547855/kotlinx-coroutines-core/jvm/src/internal/StackTraceRecovery.kt#L73
                assertSame(ex1.cause, ex2.cause)
            } else {
                assertSame(ex1, ex2)
            }
        }
    }
}
