package software.amazon.smithy.kotlin.codegen.service

import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.core.KotlinDependency
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeType
import software.amazon.smithy.model.traits.HttpTrait

class ServiceStubGenerator(
    private val settings: KotlinSettings,
    private val delegator: KotlinDelegator,
    private val serviceShapes: Set<Shape>,
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
//                    write("configureContentNegotiation()")
                }
            }
        }
    }

    private fun renderProtocolModule() {
//        delegator.useFileWriter("ProtocolModule.kt", settings.pkg.name) { writer ->
//
//            writer.withBlock("internal fun #T.configureContentNegotiation() {", "}", RuntimeTypes.KtorServerCore.Application) {
//                withBlock("#T(#T) {", "}", RuntimeTypes.KtorServerCore.install, RuntimeTypes.KtorServerContentNegotiation.ContentNegotiation) {
//                    write("#T()", RuntimeTypes.KtorServerCbor.cbor)
//                }
//            }
//        }
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

        val operationShapes = serviceShapes.filter { it.type == ShapeType.OPERATION }.map { it as OperationShape }
        delegator.useFileWriter("Routing.kt", settings.pkg.name) { writer ->

            operationShapes.forEach { shape ->
                writer.addImport("com.example.server.serde", "${shape.id.name}OperationDeserializer")
                writer.addImport("com.example.server.serde", "${shape.id.name}OperationSerializer")
                writer.addImport("com.example.server.model", "${shape.id.name}Request")
                writer.addImport("com.example.server.model", "${shape.id.name}Response")
                writer.addImport("com.example.server.operations", "${shape.id.name}HandleRequest")
            }

            writer.withBlock("internal fun #T.configureRouting(): Unit {", "}", RuntimeTypes.KtorServerCore.Application) {
                withBlock("#T {", "}", RuntimeTypes.KtorServerRouting.routing) {
                    withBlock("#T(#S) {", "}", RuntimeTypes.KtorServerRouting.get, "/") {
                        write(" #T.#T(#S)", RuntimeTypes.KtorServerCore.applicationCall, RuntimeTypes.KtorServerRouting.responseText, "hello world")
                    }
                    operationShapes.filter { it.hasTrait(HttpTrait.ID) }
                        .forEach { shape ->
                            val httpTrait = shape.getTrait<HttpTrait>() ?: error("Http trait is missing")
                            val uri = httpTrait.uri ?: error("Http trait uri is missing")
                            val method = when (httpTrait.method) {
                                "GET" -> RuntimeTypes.KtorServerRouting.get
                                "POST" -> RuntimeTypes.KtorServerRouting.post
                                else -> error("Unsupported http trait ${httpTrait.method}")
                            }
                            withBlock("#T(#S) {", "}", method, uri) {
                                write("val request = #T.#T<ByteArray>()", RuntimeTypes.KtorServerCore.applicationCall, RuntimeTypes.KtorServerRouting.requestReceive)
                                write("val deserializer = ${shape.id.name}OperationDeserializer()")
                                write("val requestObj = deserializer.deserialize(#T(), request)", RuntimeTypes.Core.ExecutionContext)
                                write("val responseObj = ${shape.id.name}HandleRequest(requestObj)")
                                write("val serializer = ${shape.id.name}OperationSerializer()")
                                write("val response = serializer.serialize(#T(), responseObj)", RuntimeTypes.Core.ExecutionContext)
                                withBlock("#T.#T(", ")", RuntimeTypes.KtorServerCore.applicationCall, RuntimeTypes.KtorServerRouting.requestRespondBytes) {
                                    write("bytes = response.body.#T() ?:  ByteArray(0),", RuntimeTypes.Http.readAll)
                                    write("contentType = #T,", RuntimeTypes.KtorServerHTTP.Cbor)
                                    write("status = #T.OK,", RuntimeTypes.KtorServerHTTP.HttpStatusCode)
                            }
                        }
                    }
                }
            }
        }
    }

    // Emits one stub handler per Smithy operation (`OperationNameHandler.kt`).
    private fun renderPerOperationHandlers() {
        val operationShapes = serviceShapes.filter { it.type == ShapeType.OPERATION }.map { it as OperationShape }
        operationShapes.forEach { shape ->
            val name = shape.id.name
            delegator.useFileWriter("${name}Operation.kt", "${settings.pkg.name}.operations") { writer ->
                writer.addImport("com.example.server.model", "${shape.id.name}Request")
                writer.addImport("com.example.server.model", "${shape.id.name}Response")

                writer.withBlock("public fun ${name}HandleRequest(req: ${name}Request): ${name}Response {", "}") {
                    write("// TODO: implement me")
                    write("// To build a ${name}Response object:")
                    write("//   1. Use`${name}Response.Builder()`")
                    write("//   2. Set fields like `${name}Response.variable = ...`")
                    write("//   3. Return the built object using `return ${name}Response.build()`")
                    write("return ${name}Response.Builder().build()")
                }
            }
        }
    }
}
