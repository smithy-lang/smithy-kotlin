package com.example

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.features.*
import io.ktor.gson.gson
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import java.time.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.toByteArray

fun main(args: Array<String>){
    embeddedServer(Netty, port = 8000, module = Application::module).start(wait = true)
}

@OptIn(ExperimentalStdlibApi::class)
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

    install(ContentNegotiation) {
        gson {
            setPrettyPrinting()
        }
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

        get("/getObject/{Bucket}/{Key}") {
            val bucket = call.parameters["Bucket"]
            val key = call.parameters["Key"]
            println("retrieving key: $key from bucket: $bucket")
            val loremIpsum = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.\n"
            val n = 50

//            call.response.headers.apply {
//                append("Content-Length", (n * loremIpsum.length).toString())
//            }
            call.respondTextWriter {
                repeat(n) {
                    write(loremIpsum)
                    flush()
                }
            }
        }

        // quick hacked together endpoint to respond to CreateAlias requests with very basic request validation
        post("/2015-03-31/functions/{FunctionName}/aliases") {
            // CreateAliasRequest handler
            val function = call.parameters["FunctionName"]
            println("create alias for: $function")

            val req = call.receive<CreateAliasRequest>()
            println(req)

            val nameBlankOrNull = req.Name?.isBlank() ?: true
            val versionBlankOrNull = req.FunctionVersion?.isBlank() ?: true

            var code = HttpStatusCode.Created
            var respBody = """
            {
                "AliasArn": "arn:aws:lambda:us-east-2:123456789012:function:my-function:LIVE",
                "Description": "alias for live version of function",
                "FunctionVersion": "1",
                "Name": "LIVE",
                "RevisionId": "873282ed-xmpl-4dc8-a069-d0c647e470c6",
                "RoutingConfig": {
                    "AdditionalVersionWeights": {
                        "1": 0.2
                    }
                }
            }
            """.trimIndent()

            if (nameBlankOrNull) {
                code = HttpStatusCode.BadRequest
                respBody = InvalidParameterValueException("invalid request", "name is required").toJson()
            } else if(versionBlankOrNull) {
                code = HttpStatusCode.BadRequest
                respBody = InvalidParameterValueException("invalid request", "version is required").toJson()
            }

            val bytes = respBody.toByteArray()
            context.respondBytes(bytes, ContentType.Application.Json, code)
        }

        // mock lambda::invoke endpoint
        post("/2015-03-31/functions/{functionName}/invocations") {
            // InvokeRequest handler
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

        put("/putObject/{bucket}/{key}") {
            val bucket = call.parameters["bucket"]
            val key = call.parameters["key"]
            println("putObject - bucket: $bucket; key: $key")
            val stream = call.receiveStream()

            val content = stream.bufferedReader().use { it.readText() }
            println("received: $content")
            stream.close()
            call.response.headers.apply {
                append("ETag", "e-tag")
                append("X-Amz-Expiration", "2022-08-22")
                append("X-Amz-Request-Charged", "request charged")
                append("x-amz-version-id", "2")
            }
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


class CreateAliasRequest {
    var Description: String? = null
    var FunctionVersion: String? = null
    var Name: String? = null
    var RoutingConfig: Map<String, String>? = null

    override fun toString(): String = buildString {
        appendln("CreateAliasRequest {")
        appendln("Description: $Description")
        appendln("FunctionVersion: $FunctionVersion")
        appendln("Name: $Name")
        if (RoutingConfig == null) {
            appendln("RoutingConfig: null")
        }else {
            appendln("RoutingConfig:")
            RoutingConfig?.forEach(){
                appendln("\t${it.key}: ${it.value}")
            }
        }
        appendln("}")
    }
}

class AliasConfiguration {
    var AliasArn: String = ""
    var Description: String = ""
    var FunctionVersion: String = ""
    var Name: String = ""
    var RevisionId: String = ""
    var RoutingConfig: MutableMap<String, String> = mutableMapOf()
}

class InvalidParameterValueException(val type: String, val message: String) {
    fun toJson(): String = """{"type": "$type", "message": "$message"}""".trimIndent()
}
