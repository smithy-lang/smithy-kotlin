/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.test.suite

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

internal fun Application.headerTests() {
    routing {
        route("non-ascii") {
            get {
                call.respond(HttpStatusCode.OK)
            }
        }

        route("echo-headers") {
            get {
                // Echo back all request headers as response headers prefixed with "x-echo-"
                // and return the count of headers whose name starts with "x-]]" (injection marker)
                val injectedCount = call.request.headers.names()
                    .count { it.startsWith("x-injected", ignoreCase = true) }
                call.response.header("x-injected-count", injectedCount.toString())
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
