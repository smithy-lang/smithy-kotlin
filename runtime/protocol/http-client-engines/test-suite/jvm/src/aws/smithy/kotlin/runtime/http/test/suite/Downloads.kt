/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.test.suite

import aws.smithy.kotlin.runtime.hashing.sha256
import aws.smithy.kotlin.runtime.util.encodeToHex
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

internal fun Application.downloadTests() {
    routing {
        route("/download") {
            get("/integrity") {
                // writer is setup to write random lengths and delay to cause the reader to enter a suspend loop
                val data = ByteArray(16 * 1024 * 1024) { it.toByte() }
                val writeSha = data.sha256().encodeToHex()
                call.response.header("expected-sha256", writeSha)

                val ch = ByteChannel(autoFlush = true)
                val content = object : OutgoingContent.ReadChannelContent() {
                    override val contentLength: Long = data.size.toLong()
                    override fun readFrom(): ByteReadChannel = ch
                    override val contentType: ContentType = ContentType.Application.OctetStream
                }

                launch {
                    var wcRemaining = data.size
                    var offset = 0
                    while (wcRemaining > 0) {
                        // random write sizes
                        val wc = minOf(wcRemaining, Random.nextInt(256, 8 * 1024))
                        val slice = data.sliceArray(offset until offset + wc)
                        ch.writeFully(slice)
                        offset += wc
                        wcRemaining -= wc

                        if (wcRemaining % 256 == 0) {
                            delay(Random.nextLong(0, 10))
                        }
                    }
                }.invokeOnCompletion { cause ->
                    ch.close(cause)
                }

                call.respond(content)
            }
        }
    }
}
