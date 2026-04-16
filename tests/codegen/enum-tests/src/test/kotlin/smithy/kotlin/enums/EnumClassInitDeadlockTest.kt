/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package smithy.kotlin.enums

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import smithy.kotlin.enums.model.ContentSource
import java.util.concurrent.TimeUnit

/**
 * Regression test for https://github.com/aws/aws-sdk-kotlin/issues/1856
 *
 * Verifies that concurrent access to a generated sealed enum class and its subclass
 * singletons does not cause a JVM class initialization deadlock. The deadlock occurs
 * when the companion object eagerly initializes a `values` list referencing subclass
 * singletons, creating a circular `<clinit>` dependency between the outer sealed class
 * and its nested objects.
 */
@Execution(ExecutionMode.CONCURRENT)
class EnumClassInitDeadlockTest {
    @Test fun testValues1() {
        assertNotNull(ContentSource.values())
    }

    @Test fun testValues2() {
        assertNotNull(ContentSource.values())
    }

    @Test fun testValues3() {
        assertNotNull(ContentSource.values())
    }

    @Test fun testOutput1() {
        assertNotNull(ContentSource.Output)
    }

    @Test fun testOutput2() {
        assertNotNull(ContentSource.Output)
    }

    @Test fun testOutput3() {
        assertNotNull(ContentSource.Output)
    }
}
