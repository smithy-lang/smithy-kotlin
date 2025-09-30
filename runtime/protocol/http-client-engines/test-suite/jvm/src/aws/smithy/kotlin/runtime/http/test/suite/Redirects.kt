/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

internal fun Application.redirectTests() {
    routing {
        route("/redirect") {
            get("/permanent") {
                call.respondRedirect("/redirect/moved", permanent = true)
            }

            get("/found") {
                call.respondRedirect("/redirect/moved", permanent = false)
            }

            get("/moved") {
                call.respondText("OK")
            }
        }
    }
}
