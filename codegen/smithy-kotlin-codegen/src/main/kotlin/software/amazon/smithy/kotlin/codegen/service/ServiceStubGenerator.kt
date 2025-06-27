package software.amazon.smithy.kotlin.codegen.service

import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.core.KotlinDependency
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes

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