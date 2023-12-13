/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.test.suite

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

internal fun Application.tlsTests() {
    routing {
        post("/tlsVerification") {
            val reqBody = call.receiveText()
            call.respondText("Received body: $reqBody")
        }
    }
}
