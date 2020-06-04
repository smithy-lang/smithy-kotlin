package com.example

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.features.*
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import java.time.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main(args: Array<String>){
    embeddedServer(Netty, port = 8000, module = Application::module).start(wait = true)
}

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module() {
    install(CORS) {
        method(HttpMethod.Options)
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        method(HttpMethod.Patch)
        header(HttpHeaders.Authorization)
        header("MyCustomHeader")
        allowCredentials = true
        anyHost() // @TODO: Don't do this in production if possible. Try to limit it.
    }

    install(io.ktor.websocket.WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        get("/") {
            call.respondText("HELLO WORLD!", contentType = ContentType.Text.Plain)
        }

        post("/getObject") {
            val lorenIpsum = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.\n"
            val times = 5
            call.respondTextWriter {
                repeat(times) {
                    write(lorenIpsum)
                    flush()
                }
            }
        }

        // mock lambda::invoke endpoint
        post("/2015-03-31/functions/{functionName}/invocations") {
            val function = call.parameters["functionName"]
            println("invoking function: $function")
            val invocationType = call.request.headers["X-Amz-Invocation-Type"]
            val logType = call.request.headers["X-Amz-Log-Type"]
            val clientCtx = call.request.headers["X-Amz-Client-Context"]
            val qualifier = call.request.queryParameters["Qualifier"]
            val payload = call.receiveText()

            println("""
                InvocationType: $invocationType
                LogType: $logType
                ClientContext: $clientCtx
                Qualifier: $qualifier
                Payload: $payload
            """.trimIndent())

            call.response.headers.apply {
                append("X-Amz-Executed-Version", "1.2")
                append("X-Amz-Log-Result", "last 4kb of execution log")
            }
            val responsePayload = """{"foo": "bar"}""".trimMargin()
            call.respondText(responsePayload, ContentType.Application.Json)
        }

        post("/putObject/{bucket}/{key}") {
            val bucket = call.parameters["bucket"]
            val key = call.parameters["key"]
            println("putObject - bucket: $bucket; key: $key")
            val stream = call.receiveStream()

            val content = stream.bufferedReader().use { it.readText() }
            println("received: $content")
            stream.close()
            call.respond(HttpStatusCode.OK)
        }

//        webSocket("/myws/echo") {
//            send(Frame.Text("Hi from server"))
//            while (true) {
//                val frame = incoming.receive()
//                if (frame is Frame.Text) {
//                    send(Frame.Text("Client said: " + frame.readText()))
//                }
//            }
//        }
    }
}

