package software.amazon.smithy.kotlin.codegen.service

import software.amazon.smithy.kotlin.codegen.core.GenerationContext
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.core.KotlinDependency
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.defaultName
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.OperationShape

class ServiceStubGenerator(
    private val ctx: GenerationContext,
    private val delegator: KotlinDelegator,
) {
    private val serviceShape = ctx.settings.getService(ctx.model)

    private val operations: List<OperationShape> = TopDownIndex
        .of(ctx.model)
        .getContainedOperations(serviceShape)
        .sortedBy { it.defaultName() }

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
        val port = ctx.settings.serviceStub.port
        val engine = when (ctx.settings.serviceStub.engine) {
            ServiceEngine.NETTY -> RuntimeTypes.KtorServerNetty.Netty
        }
        delegator.useFileWriter("Main.kt", ctx.settings.pkg.name) { writer ->
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
        delegator.useFileWriter("ProtocolModule.kt", ctx.settings.pkg.name) { writer ->

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
        delegator.useFileWriter("Routing.kt", ctx.settings.pkg.name) { writer ->
            writer.addImport(RuntimeTypes.KtorServerCore.applicationCall)
            writer.addImport(RuntimeTypes.KtorServerRouting.responseText)
            writer.addImport(RuntimeTypes.KtorServerRouting.requestReceive)
            writer.addImport(RuntimeTypes.KtorServerRouting.requestRespondBytes)
            writer.addImport(RuntimeTypes.KtorServerHttp.ContentType)

            operations
                .forEach { shape ->
                    writer.addImport("com.example.server.serde", "${shape.id.name}OperationDeserializer")
                    writer.addImport("com.example.server.serde", "${shape.id.name}OperationSerializer")
                    writer.addImport("com.example.server.model", "${shape.id.name}Request")
                    writer.addImport("com.example.server.model", "${shape.id.name}Response")
                }

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
                            write("contentType = #T,", RuntimeTypes.KtorServerHttp.Cbor)
                            write("status = #T.OK,", RuntimeTypes.KtorServerHttp.HttpStatusCode)
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
