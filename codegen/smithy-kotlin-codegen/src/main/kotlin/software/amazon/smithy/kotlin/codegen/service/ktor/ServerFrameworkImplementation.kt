package software.amazon.smithy.kotlin.codegen.service.ktor

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.core.withInlineBlock
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.service.ServiceTypes

internal fun KtorStubGenerator.writeServerFrameworkImplementation(writer: KotlinWriter) {
    writer.withBlock("internal fun #T.module(): Unit {", "}", RuntimeTypes.KtorServerCore.Application) {
        write("#T()", ServiceTypes(pkgName).configureLogging)
        withBlock("#T(#T) {", "}", RuntimeTypes.KtorServerCore.install, RuntimeTypes.KtorServerBodyLimit.RequestBodyLimit) {
            write("bodyLimit { #T.requestBodyLimit }", ServiceTypes(pkgName).serviceFrameworkConfig)
        }
        write("#T(#T)", RuntimeTypes.KtorServerCore.install, RuntimeTypes.KtorServerDoubleReceive.DoubleReceive)
        write("#T()", ServiceTypes(pkgName).configureErrorHandling)
        write("#T()", ServiceTypes(pkgName).configureAuthentication)
        write("#T()", ServiceTypes(pkgName).configureRouting)
    }
        .write("")
    writer.withBlock("internal class KtorServiceFramework() : ServiceFramework {", "}") {
        write("private var engine: #T<*, *>? = null", RuntimeTypes.KtorServerCore.EmbeddedServerType)
        write("")
        write("private val engineFactory = #T.engine.toEngineFactory()", ServiceTypes(pkgName).serviceFrameworkConfig)

        write("")
        withBlock("override fun start() {", "}") {
            withInlineBlock("engine = #T(", ")", RuntimeTypes.KtorServerCore.embeddedServer) {
                write("engineFactory,")
                withBlock("configure = {", "}") {
                    withBlock("#T {", "}", RuntimeTypes.KtorServerCore.connector) {
                        write("host = #S", "0.0.0.0")
                        write("port = #T.port", ServiceTypes(pkgName).serviceFrameworkConfig)
                    }
                    withBlock("when (this) {", "}") {
                        withBlock("is #T -> {", "}", RuntimeTypes.KtorServerNetty.Configuration) {
                            write("requestReadTimeoutSeconds = #T.requestReadTimeoutSeconds", ServiceTypes(pkgName).serviceFrameworkConfig)
                            write("responseWriteTimeoutSeconds = #T.responseWriteTimeoutSeconds", ServiceTypes(pkgName).serviceFrameworkConfig)
                        }

                        withBlock("is #T -> {", "}", RuntimeTypes.KtorServerCio.Configuration) {
                            write("connectionIdleTimeoutSeconds = #T.requestReadTimeoutSeconds", ServiceTypes(pkgName).serviceFrameworkConfig)
                        }

                        withBlock("is #T -> {", "}", RuntimeTypes.KtorServerJettyJakarta.Configuration) {
                            write(
                                "idleTimeout = #T.requestReadTimeoutSeconds.#T",
                                ServiceTypes(pkgName).serviceFrameworkConfig,
                                KotlinTypes.Time.seconds,
                            )
                        }
                    }
                }
            }
            write("{ #T() }", ServiceTypes(pkgName).module)
            write("engine?.apply { start(wait = true) }")
        }
        write("")
        withBlock("final override fun close() {", "}") {
            write("engine?.stop(#T.closeGracePeriodMillis, #T.closeTimeoutMillis)", ServiceTypes(pkgName).serviceFrameworkConfig, ServiceTypes(pkgName).serviceFrameworkConfig)
            write("engine = null")
        }
    }
}
