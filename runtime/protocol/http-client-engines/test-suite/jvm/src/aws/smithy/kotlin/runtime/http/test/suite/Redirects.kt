/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*

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
