package software.amazon.smithy.kotlin.codegen.service

import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.core.KotlinDependency
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock

class ServiceStubGenerator(
    private val packageName: String,
    private val delegator: KotlinDelegator,
) {

    fun render() {
        renderMainFile()
        renderProtocolModule()
        renderAuthModule()
        renderConstraintValidators()
        renderRouting()
        renderPerOperationHandlers()
    }

    // Writes `Main.kt` that boots the embedded Ktor service.
    private fun renderMainFile() {
        delegator.useFileWriter("Main.kt", packageName) { writer ->
            writer.dependencies.addAll(KotlinDependency.KTOR_LOGGING_BACKEND.dependencies)

            writer.withBlock("public fun main(): Unit {", "}") {
                withBlock("#T(#T, port = 8080) {", "}.start(wait = true)", RuntimeTypes.KtorServerCore.embeddedServer, RuntimeTypes.KtorServerNetty.Netty) {
                    write("configureRouting()")
                    write("configureContentNegotiation()")
                }
            }
        }
    }

    private fun renderProtocolModule() {
        delegator.useFileWriter("ProtocolModule.kt", packageName) { writer ->

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
        delegator.useFileWriter("Routing.kt", packageName) { writer ->
            writer.addImport(RuntimeTypes.KtorServerCore.applicationCall)
            writer.addImport(RuntimeTypes.KtorServerCore.responseText)

            writer.withBlock("internal fun #T.configureRouting(): Unit {", "}", RuntimeTypes.KtorServerCore.Application) {
                withBlock("#T {", "}", RuntimeTypes.KtorServerRouting.routing) {
                    withBlock("#T(#S) {", "}", RuntimeTypes.KtorServerRouting.get, "/") {
                        write(" call.respondText(#S)", "hello world")
                    }
                }
            }
        }
    }

    // Emits one stub handler per Smithy operation (`OperationNameHandler.kt`).
    private fun renderPerOperationHandlers() {
    }
}
