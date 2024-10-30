/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.test.suite

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.jetty.JettyApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingApplicationCall
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.InternalAPI
import io.ktor.utils.io.close
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

internal fun Application.connectionTests() {
    routing {
        route("connectionDrop") {
            @OptIn(InternalAPI::class)
            post {
                val routingCall = call as RoutingApplicationCall
                val jettyCall = routingCall.engineCall as JettyApplicationCall

                launch {
                    delay(4.seconds) // Close the connection ~4 seconds after the call ends
                    jettyCall.response.responseChannel().close()
                }

                jettyCall.respondText("Bar")
            }
        }
    }
}
