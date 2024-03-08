/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.test

import aws.smithy.kotlin.runtime.content.asByteStream
import aws.smithy.kotlin.runtime.content.toByteArray
import aws.smithy.kotlin.runtime.content.writeToFile
import aws.smithy.kotlin.runtime.hashing.sha256
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.complete
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.test.util.AbstractEngineTest
import aws.smithy.kotlin.runtime.http.test.util.test
import aws.smithy.kotlin.runtime.http.test.util.testSetup
import aws.smithy.kotlin.runtime.testing.RandomTempFile
import aws.smithy.kotlin.runtime.text.encoding.encodeToHex
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

// extra sanity check that files work end-to-end
class FileUploadDownloadTest : AbstractEngineTest() {

    @Test
    fun testUploadIntegrityFileContent() = testEngines {
        test { _, client ->
            val file = RandomTempFile(5 * 1024 * 1024) // 5MB
            val httpBody = file.asByteStream().toHttpBody()
            val expectedSha = file.readBytes().sha256().encodeToHex()

            val req = HttpRequest {
                method = HttpMethod.POST
                testSetup()
                url.path.decoded = "/upload/content"
                body = httpBody
            }

            val call = client.call(req)
            call.complete()
            assertEquals(HttpStatusCode.OK, call.response.status)
            assertEquals(expectedSha, call.response.headers["content-sha256"])
        }
    }

    @Test
    fun testDownloadIntegrityFileContent() = testEngines {
        test { _, client ->
            val req = HttpRequest {
                testSetup()
                url.path.decoded = "/download/integrity"
            }

            val call = client.call(req)
            try {
                assertEquals(HttpStatusCode.OK, call.response.status)

                val expectedSha256 = call.response.headers["expected-sha256"] ?: fail("missing expected-sha256 header")
                val contentLength = call.response.body.contentLength ?: fail("expected Content-Length")
                check(contentLength < Int.MAX_VALUE)

                val body = call.response.body
                val stream = checkNotNull(body.toByteStream())

                val file = RandomTempFile(0)
                stream.writeToFile(file)
                val readSha256 = file.readBytes().sha256().encodeToHex()
                assertEquals(expectedSha256, readSha256)
                assertEquals(contentLength, file.length())
            } finally {
                call.complete()
            }
        }
    }

    // https://github.com/awslabs/smithy-kotlin/issues/1041
    @Test
    fun testDownloadCompressedPayload() = testEngines {
        test { _, client ->
            val req = HttpRequest {
                testSetup()
                url.path.decoded = "/download/gzipped"
            }

            val call = client.call(req)
            try {
                assertEquals(HttpStatusCode.OK, call.response.status)
                assertEquals("gzip", call.response.headers["Content-Encoding"]) // Verify response isn't decompressed

                val body = checkNotNull(call.response.body.toByteStream()).toByteArray()
                assertTrue(body.size < 1024) // Uncompressed payload is 1024 bytes; verify compressed body is smaller

                val uncompressed = ByteArrayInputStream(body).use { baStream ->
                    GZIPInputStream(baStream).use { gzStream ->
                        gzStream.readBytes()
                    }
                }
                assertEquals(1024, uncompressed.size)
            } finally {
                call.complete()
            }
        }
    }
}
