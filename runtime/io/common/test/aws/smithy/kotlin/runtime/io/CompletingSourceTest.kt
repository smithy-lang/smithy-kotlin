/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.hashing.*
import aws.smithy.kotlin.runtime.util.encodeBase64String
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CompletingSourceTest {

    @Test
    fun testCompleted() = runTest {
        val hashFunctionName = "crc32"

        val byteArray = ByteArray(19456) { 0xf }
        val source = byteArray.source()
        val hashingSource = HashingSource(hashFunctionName.toHashFunction()!!, source)

        val completableDeferred = CompletableDeferred<String>()

        val completingSource = CompletingSource(completableDeferred, hashingSource)

        completingSource.read(SdkBuffer(), 1L)
        assertFalse(completableDeferred.isCompleted) // deferred value should not be completed because the source is not exhausted
        completingSource.readToByteArray() // source is now exhausted

        val expectedHash = hashFunctionName.toHashFunction()!!
        expectedHash.update(byteArray)

        assertTrue(completableDeferred.isCompleted)
        assertEquals(expectedHash.digest().encodeBase64String(), completableDeferred.getCompleted())
    }
}
