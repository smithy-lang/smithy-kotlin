/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.test.suite

import aws.smithy.kotlin.runtime.hashing.sha256
import aws.smithy.kotlin.runtime.text.encoding.encodeToHex
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.random.Random

internal const val DOWNLOAD_SIZE = 16L * 1024 * 1024 // 16MB

internal fun Application.downloadTests() {
    routing {
        route("/download") {
            get("/integrity") {
                // writer is setup to write random lengths and delay to cause the reader to enter a suspend loop
                val data = ByteArray(DOWNLOAD_SIZE.toInt()) { it.toByte() }
                val writeSha = data.sha256().encodeToHex()
                call.response.header("expected-sha256", writeSha)
                val chunked = call.request.queryParameters["chunked-response"]?.toBoolean() ?: false

                call.respondBytesWriter(contentLength = DOWNLOAD_SIZE.takeUnless { chunked }) {
                    var wcRemaining = data.size
                    var offset = 0
                    while (wcRemaining > 0) {
                        // random write sizes
                        val wc = minOf(wcRemaining, Random.nextInt(256, 8 * 1024))
                        val slice = data.sliceArray(offset until offset + wc)
                        writeFully(slice)
                        offset += wc
                        wcRemaining -= wc

                        if (wcRemaining % 256 == 0) {
                            delay(Random.nextLong(0, 10))
                        }
                    }
                }
            }

            get("/empty") {
                call.response.status(HttpStatusCode.OK)
                call.response.header("x-foo", "foo")
                call.response.header("x-bar", "bar")
            }

            get("/gzipped") {
                val uncompressed = ByteArray(1024) { it.toByte() }
                val compressed = ByteArrayOutputStream().use { baStream ->
                    GZIPOutputStream(baStream).use { gzStream ->
                        gzStream.write(uncompressed)
                        gzStream.flush()
                    }
                    baStream.flush()
                    baStream.toByteArray()
                }

                call.response.header("Content-Encoding", "gzip")
                call.respondBytes(compressed)
            }
        }
    }
}
