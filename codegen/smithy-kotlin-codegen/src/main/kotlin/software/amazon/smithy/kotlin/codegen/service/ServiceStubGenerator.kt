package software.amazon.smithy.kotlin.codegen.service

import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.core.KotlinDependency
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.serde.CborParserGenerator
import software.amazon.smithy.kotlin.codegen.rendering.serde.CborSerializerGenerator

class ServiceStubGenerator (
    private val packageName: String,
    private val delegator: KotlinDelegator
) {



    fun render() {

        renderMainFile()
        renderProtocolModule()
        renderAuthModule()
        renderConstraintValidators()
        renderRouting()
        renderPerOperationHandlers()
    }


    //Writes `Main.kt` that boots the embedded Ktor service.
    private fun renderMainFile() {
        delegator.useFileWriter("Main.kt", packageName) { writer ->

            writer.addImport(RuntimeTypes.KtorServerCore.embeddedServer)
            writer.addImport(RuntimeTypes.KtorServerNetty.Netty)
            writer.dependencies.addAll(KotlinDependency.KTOR_LOGGING_BACKEND.dependencies)

            writer.openBlock("public fun main(): Unit {")
                    .openBlock("embeddedServer(Netty, port = 8080) {")
                        .write("configureRouting()")
                        .write("configureContentNegotiation()")
                    .closeBlock("}.start(wait = true)")
                .closeBlock("}")

        }
    }

    // Emits `ProtocolModule.kt` containing content-negotiation install logic.
    fun renderCustomProtocolModule(protocolGenerator: ProtocolGenerator) {
        delegator.useFileWriter("ProtocolModule.kt", packageName) { writer ->
            // Add necessary imports
            writer.addImport(RuntimeTypes.KtorServerCore.application)
            writer.addImport(RuntimeTypes.KtorServerCore.install)
            writer.addImport(RuntimeTypes.KtorServerContentNegotiation.all)
            writer.addImport(RuntimeTypes.KtorServerCbor.all)

            writer.openBlock("internal fun Application.configureContentNegotiation() {")
                .openBlock("install(ContentNegotiation) {")
                .write("cbor()")
                .closeBlock("}")
                .closeBlock("}")

            // Generate CBOR serialization/deserialization code
            val cborSerializer = CborSerializerGenerator(protocolGenerator)
            val cborParser = CborParserGenerator(protocolGenerator)

            // For each model shape that needs serialization/deserialization
//            for (shape in getShapesRequiringSerDe()) { // You'll need to implement this method
//                val members = shape.members().toList()
//
//                // Generate serializer
//                val serializerSymbol = cborSerializer.payloadSerializer(
//                    getGenerationContext(), // You'll need to implement this method
//                    shape,
//                    members
//                )
//
//                // Generate deserializer
//                val deserializerSymbol = cborParser.payloadDeserializer(
//                    getGenerationContext(),
//                    shape,
//                    members
//                )
//
//                // Add the generated symbols to imports
//                writer.addImport(serializerSymbol)
//                writer.addImport(deserializerSymbol)
//            }
        }
    }

    private fun renderProtocolModule() {
        delegator.useFileWriter("ProtocolModule.kt", packageName) { writer ->

            writer.addImport(RuntimeTypes.KtorServerCore.application)
            writer.addImport(RuntimeTypes.KtorServerCore.install)
            writer.addImport(RuntimeTypes.KtorServerContentNegotiation.all)
            writer.addImport(RuntimeTypes.KtorServerCbor.all)

            writer.openBlock("internal fun Application.configureContentNegotiation() {")
                .openBlock("install(ContentNegotiation) {")
                .write("cbor()")
                .closeBlock("}")
                .closeBlock("}")

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
        delegator.useFileWriter("Routing.kt", packageName) { writer ->
            writer.addImport(RuntimeTypes.KtorServerCore.application)
            writer.addImport(RuntimeTypes.KtorServerCore.responseAll)
            writer.addImport(RuntimeTypes.KtorServerCore.routingAll)


            writer.openBlock("internal fun Application.configureRouting(): Unit {")
                    .openBlock("routing {")
                        .openBlock("get(\"/\") {")
                            .write("call.respondText(\"hello world\")")
                        .closeBlock("}")
                    .closeBlock("}")
                .closeBlock("}")
        }

    }

    // Emits one stub handler per Smithy operation (`OperationNameHandler.kt`).
    private fun renderPerOperationHandlers() {

    }
}