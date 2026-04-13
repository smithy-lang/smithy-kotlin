/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package smithy.kotlin.enums

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import smithy.kotlin.enums.model.ContentSource
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Regression test for https://github.com/aws/aws-sdk-kotlin/issues/1856
 *
 * Verifies that concurrent access to a generated sealed enum class and its subclass
 * singletons does not cause a JVM class initialization deadlock. The deadlock occurs
 * when the companion object eagerly initializes a `values` list referencing subclass
 * singletons, creating a circular `<clinit>` dependency between the outer sealed class
 * and its nested objects.
 */
class EnumClassInitDeadlockTest {
    @Test
    fun testConcurrentEnumAccessDoesNotDeadlock(): Unit = runBlocking {
        val jobs = (1..50).map { i ->
            async(Dispatchers.Default) {
                if (i % 2 == 0) {
                    // Triggers ContentSource companion <clinit>
                    assertNotNull(ContentSource.values())
                } else {
                    // Triggers ContentSource.Output <clinit>
                    assertNotNull(ContentSource.Output)
                }
            }
        }
        jobs.awaitAll()
    }
}
