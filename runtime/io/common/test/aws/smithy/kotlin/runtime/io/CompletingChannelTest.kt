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
class CompletingChannelTest {

    @Test
    fun testCompleted() = runTest {
        val hashFunctionName = "sha256"

        val byteArray = ByteArray(2143) { 0xf }
        val channel = SdkByteReadChannel(byteArray)
        val hashingChannel = HashingByteReadChannel(hashFunctionName.toHashFunction()!!, channel)

        val completableDeferred = CompletableDeferred<String>()

        val completingChannel = CompletingByteReadChannel(completableDeferred, hashingChannel)

        completingChannel.read(SdkBuffer(), 1L)
        assertFalse(completableDeferred.isCompleted)

        completingChannel.readAll(SdkBuffer())

        val expectedHash = hashFunctionName.toHashFunction()!!
        expectedHash.update(byteArray)

        assertTrue(completableDeferred.isCompleted)
        assertEquals(expectedHash.digest().encodeBase64String(), completableDeferred.getCompleted())
    }
}
