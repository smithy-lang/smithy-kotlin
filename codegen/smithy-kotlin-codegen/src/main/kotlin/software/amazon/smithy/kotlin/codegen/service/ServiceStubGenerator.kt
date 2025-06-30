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
        delegator.useFileWriter("Routing.kt", settings.pkg.name) { writer ->
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
