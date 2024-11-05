/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.test.suite

import aws.smithy.kotlin.runtime.hashing.Sha256
import aws.smithy.kotlin.runtime.text.encoding.encodeToHex
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.availableForRead
import io.ktor.utils.io.readAvailable

private const val CHUNK_SIZE = 1024 * 8

internal fun Application.uploadTests() {
    routing {
        route("upload") {
            post("content") {
                val contentSha = Sha256()
                val ch = call.request.receiveChannel()

                val buffer = ByteArray(CHUNK_SIZE)
                while (!ch.isClosedForRead || ch.availableForRead > 0) {
                    val rc = ch.readAvailable(buffer, 0, buffer.size)
                    if (rc <= 0) break

                    val slice = if (rc == buffer.size) buffer else buffer.sliceArray(0 until rc)
                    contentSha.update(slice)
                }

                call.response.header("content-sha256", contentSha.digest().encodeToHex())
                call.request.headers["Content-Type"]?.let { call.response.header("request-content-type", it) }
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
