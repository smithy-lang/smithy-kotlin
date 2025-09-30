/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.test.suite

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

internal fun Application.concurrentTests() {
    routing {
        route("concurrent") {
            get {
                val respSize = 32 * 1024
                val text = "testing"
                call.respondText(text.repeat(respSize / text.length))
            }
        }

        route("slow") {
            get {
                val chunk = ByteArray(256) { it.toByte() }
                call.respondOutputStream {
                    repeat(10) {
                        delay(200.milliseconds)
                        write(chunk)
                    }
                }
            }
        }
    }
}
