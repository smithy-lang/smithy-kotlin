/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.test

import aws.smithy.kotlin.runtime.hashing.Sha256
import aws.smithy.kotlin.runtime.hashing.sha256
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.complete
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.test.util.AbstractEngineTest
import aws.smithy.kotlin.runtime.http.test.util.test
import aws.smithy.kotlin.runtime.http.test.util.testSetup
import aws.smithy.kotlin.runtime.http.toSdkByteReadChannel
import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.text.encoding.encodeToHex
import kotlin.test.*

class DownloadTest : AbstractEngineTest() {

    // These download integrity tests assert behavior on various SdkByteReadChannel.read* functions (specifically
    // that reading doesn't lose any bytes due to erroneous handling of buffers). The server
    // side will insert random delays and ensures that the reader will suspend while reading
    // see https://github.com/awslabs/aws-sdk-kotlin/issues/526

    @Test
    fun testReadFullyIntegrity() =
        runReadSuspendIntegrityTest { channel, totalSize ->
            val dest = SdkBuffer()
            channel.readFully(dest, totalSize.toLong())
            dest.readByteArray().sha256().encodeToHex()
        }

    @Test
    fun testReadAvailableIntegrity() =
        runReadSuspendIntegrityTest { channel, totalSize ->
            val checksum = Sha256()
            var totalRead = 0
            while (!channel.isClosedForRead) {
                val sink = SdkBuffer()
                val rc = channel.read(sink, 8192).toInt()
                if (rc < 0) break

                totalRead += rc
                val chunk = sink.readByteArray()
                checksum.update(chunk)
            }

            assertEquals(totalSize, totalRead)
            checksum.digest().encodeToHex()
        }

    @Test
    fun testReadRemainingIntegrity() =
        runReadSuspendIntegrityTest { channel, totalSize ->
            val data = SdkBuffer()
            channel.readRemaining(data)
            assertEquals(totalSize.toLong(), data.size)
            data.readByteArray().sha256().encodeToHex()
        }

    private fun runReadSuspendIntegrityTest(reader: suspend (SdkByteReadChannel, Int) -> String) = testEngines {
        test { env, client ->
            val req = HttpRequest {
                testSetup(env)
                url.path = "/download/integrity"
            }

            val call = client.call(req)
            try {
                assertEquals(HttpStatusCode.OK, call.response.status)

                val expectedSha256 = call.response.headers["expected-sha256"] ?: fail("missing expected-sha256 header")
                val contentLength = call.response.body.contentLength ?: fail("expected Content-Length")
                check(contentLength < Int.MAX_VALUE)

                val body = call.response.body
                val chan = requireNotNull(body.toSdkByteReadChannel())

                val readSha256 = reader(chan, contentLength.toInt())
                assertEquals(expectedSha256, readSha256)
            } finally {
                call.complete()
            }
        }
    }

    @Test
    fun testChunkedResponse() = testEngines {
        test { env, client ->
            val req = HttpRequest {
                testSetup(env)
                url.path = "/download/integrity"
                url.parameters.append("chunked-response", "true")
            }

            val call = client.call(req)
            try {
                assertEquals(HttpStatusCode.OK, call.response.status)

                assertEquals("chunked", call.response.headers["Transfer-Encoding"]?.lowercase())

                val expectedSha256 = call.response.headers["expected-sha256"] ?: fail("missing expected-sha256 header")
                assertNull(call.response.body.contentLength, "${client.engine}")

                val body = call.response.body
                val chan = requireNotNull(body.toSdkByteReadChannel())
                val bytes = chan.readToBuffer().readByteArray()
                val actualSha256 = bytes.sha256().encodeToHex()
                assertEquals(expectedSha256, actualSha256)
            } finally {
                call.complete()
            }
        }
    }

    @Test
    fun testEmptyPayloadRepresentation() = testEngines {
        // We have behavior built on top of how an empty payload is represented, ensure it is consitent
        // across engines, see https://github.com/awslabs/aws-sdk-kotlin/issues/638
        test { env, client ->
            val req = HttpRequest {
                testSetup(env)
                url.path = "/download/empty"
            }

            val call = client.call(req)
            try {
                assertEquals(HttpStatusCode.OK, call.response.status)
                assertEquals(0, call.response.body.contentLength, "${client.engine}")

                val body = call.response.body
                assertIs<HttpBody.Empty>(body, "${client.engine}")
            } finally {
                call.complete()
            }
        }
    }
}
