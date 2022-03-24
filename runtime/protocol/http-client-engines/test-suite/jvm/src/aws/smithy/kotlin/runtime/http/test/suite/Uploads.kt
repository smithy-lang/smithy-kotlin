/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.test.suite

import aws.smithy.kotlin.runtime.util.Sha256
import aws.smithy.kotlin.runtime.util.encodeToHex
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*

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
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
