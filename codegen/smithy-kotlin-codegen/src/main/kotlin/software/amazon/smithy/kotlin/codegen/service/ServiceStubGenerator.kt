package software.amazon.smithy.kotlin.codegen.service

import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.core.KotlinDependency
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock

class ServiceStubGenerator(
    private val settings: KotlinSettings,
    private val delegator: KotlinDelegator,
) {

    fun render() {
        // FIXME: check server framework here and render according to the chosen server framework
        renderMainFile()
        renderProtocolModule()
        renderAuthModule()
        renderConstraintValidators()
        renderRouting()
        renderPerOperationHandlers()
    }

    // Writes `Main.kt` that boots the embedded Ktor service.
    private fun renderMainFile() {
        val port = settings.serviceStub.port
        val engine = when (settings.serviceStub.engine) {
            ServiceEngine.NETTY -> RuntimeTypes.KtorServerNetty.Netty
        }
        delegator.useFileWriter("Main.kt", settings.pkg.name) { writer ->
            writer.dependencies.addAll(KotlinDependency.KTOR_LOGGING_BACKEND.dependencies)

            writer.withBlock("public fun main(): Unit {", "}") {
                withBlock(
                    "#T(#T, port = $port) {",
                    "}.start(wait = true)",
                    RuntimeTypes.KtorServerCore.embeddedServer,
                    engine,
                ) {
                    write("configureRouting()")
                    write("configureContentNegotiation()")
                }
            }
        }
    }

    private fun renderProtocolModule() {
        delegator.useFileWriter("ProtocolModule.kt", settings.pkg.name) { writer ->

            writer.withBlock("internal fun #T.configureContentNegotiation() {", "}", RuntimeTypes.KtorServerCore.Application) {
                withBlock("#T(#T) {", "}", RuntimeTypes.KtorServerCore.install, RuntimeTypes.KtorServerContentNegotiation.ContentNegotiation) {
                    write("#T()", RuntimeTypes.KtorServerCbor.cbor)
                }
            }
        }
    }

    // Generates `Authentication.kt` with Authenticator interface + configureSecurity().
    private fun renderAuthModule() {
    }

    // For every operation request structure, create a `validate()` function file.
    private fun renderConstraintValidators() {
    }

    // Writes `Routing.kt` that maps Smithy operations → Ktor routes.
    private fun renderRouting() {
        // FIXME: currently it is hardcoded for testing. This part will be generated once I'm working on the routing.
        delegator.useFileWriter("Routing.kt", settings.pkg.name) { writer ->
            writer.addImport(RuntimeTypes.KtorServerCore.applicationCall)
            writer.addImport(RuntimeTypes.KtorServerRouting.responseText)
            writer.addImport(RuntimeTypes.KtorServerRouting.post)
            writer.addImport(RuntimeTypes.KtorServerRouting.requestReceive)
            writer.addImport(RuntimeTypes.KtorServerRouting.requestRespondBytes)

            writer.addImport(RuntimeTypes.KtorServerHTTP.ContentType)
            writer.addImport(RuntimeTypes.KtorServerHTTP.HttpStatusCode)
            writer.addImport(RuntimeTypes.KtorServerHTTP.Cbor)

            writer.write("import com.example.server.serde.SayHelloOperationDeserializer")
            writer.write("import com.example.server.serde.SayHelloOperationSerializer")
            writer.write("import com.example.server.model.SayHelloRequest")
            writer.write("import com.example.server.model.SayHelloResponse")
            writer.write("import aws.smithy.kotlin.runtime.http.readAll")

            writer.write(
                "public fun handleRequest(req: SayHelloRequest): SayHelloResponse { \n" +
                    "val builder = SayHelloResponse.Builder() \n" +
                    "builder.greeting = #S \n" +
                    "return builder.build() \n" +
                    "}",
                "Hello Luigi",
            )

            writer.withBlock("internal fun #T.configureRouting(): Unit {", "}", RuntimeTypes.KtorServerCore.Application) {
                withBlock("#T {", "}", RuntimeTypes.KtorServerRouting.routing) {
                    withBlock("#T(#S) {", "}", RuntimeTypes.KtorServerRouting.get, "/") {
                        write(" call.respondText(#S)", "hello world")
                    }
                    withBlock("#T(#S) {", "}", RuntimeTypes.KtorServerRouting.post, "/greeting") {
                        write("val requestBytes = call.receive<ByteArray>()")
                        write("val deserializer = SayHelloOperationDeserializer()")
                        write("val requestObj = deserializer.deserialize(#T(), requestBytes)", RuntimeTypes.Core.ExecutionContext)
                        write("val responseObj = handleRequest(requestObj)")
                        write("val serializer = SayHelloOperationSerializer()")
                        write("val responseBytes = serializer.serialize(#T(), responseObj)", RuntimeTypes.Core.ExecutionContext)
                        withBlock("call.respondBytes(", ")") {
                            write("bytes = responseBytes.body.#T() ?:  ByteArray(0),", RuntimeTypes.Http.readAll)
                            write("contentType = #T,", RuntimeTypes.KtorServerHTTP.Cbor)
                            write("status = HttpStatusCode.OK,")
                        }
                    }
                }
            }
        }
    }

    // Emits one stub handler per Smithy operation (`OperationNameHandler.kt`).
    private fun renderPerOperationHandlers() {
    }
}
