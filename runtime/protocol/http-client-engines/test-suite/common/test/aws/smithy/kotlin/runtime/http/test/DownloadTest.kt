/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.test

import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.http.response.complete
import aws.smithy.kotlin.runtime.http.test.util.AbstractEngineTest
import aws.smithy.kotlin.runtime.http.test.util.test
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.util.Sha256
import aws.smithy.kotlin.runtime.util.encodeToHex
import aws.smithy.kotlin.runtime.util.sha256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

class DownloadTest : AbstractEngineTest() {

    // These download integrity tests assert behavior on various SdkByteReadChannel.read* functions (specifically
    // that reading doesn't lose any bytes due to erroneous handling of buffers). The server
    // side will insert random delays and ensures that the reader will suspend while reading

    @Test
    fun testReadFullyIntegrity() =
        // see https://github.com/awslabs/aws-sdk-kotlin/issues/526
        runReadSuspendIntegrityTest { channel, totalSize ->
            val dest = ByteArray(totalSize)
            channel.readFully(dest)
            dest.sha256().encodeToHex()
        }

    @Test
    fun testReadAvailableIntegrity() =
        runReadSuspendIntegrityTest { channel, totalSize ->
            val checksum = Sha256()
            var totalRead = 0
            while (!channel.isClosedForRead) {
                val chunk = ByteArray(8 * 1024)
                val rc = channel.readAvailable(chunk)
                if (rc < 0) break

                totalRead += rc
                val slice = if (rc != chunk.size) chunk.sliceArray(0 until rc) else chunk
                checksum.update(slice)
            }

            assertEquals(totalSize, totalRead)
            checksum.digest().encodeToHex()
        }

    @Test
    fun testReadRemainingIntegrity() =
        runReadSuspendIntegrityTest { channel, totalSize ->
            val data = channel.readRemaining()
            assertEquals(totalSize, data.size)
            data.sha256().encodeToHex()
        }

    private fun runReadSuspendIntegrityTest(reader: suspend (SdkByteReadChannel, Int) -> String) = testEngines {
        test { env, client ->
            val req = HttpRequest {
                url(env.testServer)
                url.path = "/download/integrity"
            }

            val call = client.call(req)
            try {
                assertEquals(HttpStatusCode.OK, call.response.status)

                val expectedSha256 = call.response.headers["expected-sha256"] ?: fail("missing expected-sha256 header")
                val contentLength = call.response.body.contentLength ?: fail("expected Content-Length")
                check(contentLength < Int.MAX_VALUE)

                val body = call.response.body
                assertIs<HttpBody.Streaming>(body)
                val chan = body.readFrom()

                val readSha256 = reader(chan, contentLength.toInt())
                assertEquals(expectedSha256, readSha256)
            } finally {
                call.complete()
            }
        }
    }
}
