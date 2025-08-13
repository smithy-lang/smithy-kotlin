package software.amazon.smithy.kotlin.codegen.service.ktor

import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.service.KtorStubGenerator
import software.amazon.smithy.kotlin.codegen.service.LoggingWriter
import software.amazon.smithy.kotlin.codegen.service.ServiceTypes

internal fun KtorStubGenerator.writeUtils() {
    renderLogging()
}

private fun KtorStubGenerator.renderLogging() {
    delegator.useFileWriter("Logging.kt", "$pkgName.utils") { writer ->

        writer.withBlock("internal fun #T.configureLogging() {", "}", RuntimeTypes.KtorServerCore.Application) {
            withBlock(
                "val slf4jLevel: #T? = when (#T.logLevel) {",
                "}",
                RuntimeTypes.KtorLoggingSlf4j.Level,
                ServiceTypes(pkgName).serviceFrameworkConfig,
            ) {
                write("#T.INFO -> #T.INFO", ServiceTypes(pkgName).logLevel, RuntimeTypes.KtorLoggingSlf4j.Level)
                write("#T.TRACE -> #T.TRACE", ServiceTypes(pkgName).logLevel, RuntimeTypes.KtorLoggingSlf4j.Level)
                write("#T.DEBUG -> #T.DEBUG", ServiceTypes(pkgName).logLevel, RuntimeTypes.KtorLoggingSlf4j.Level)
                write("#T.WARN -> #T.WARN", ServiceTypes(pkgName).logLevel, RuntimeTypes.KtorLoggingSlf4j.Level)
                write("#T.ERROR -> #T.ERROR", ServiceTypes(pkgName).logLevel, RuntimeTypes.KtorLoggingSlf4j.Level)
                write("#T.OFF -> null", ServiceTypes(pkgName).logLevel)
            }
            write("")
            write("val logbackLevel = slf4jLevel?.let { #T.valueOf(it.name) } ?: #T.OFF", RuntimeTypes.KtorLoggingLogback.Level, RuntimeTypes.KtorLoggingLogback.Level)
            write("")
            write(
                "(#T.getILoggerFactory() as #T).getLogger(#T).level = logbackLevel",
                RuntimeTypes.KtorLoggingSlf4j.LoggerFactory,
                RuntimeTypes.KtorLoggingLogback.LoggerContext,
                RuntimeTypes.KtorLoggingSlf4j.ROOT_LOGGER_NAME,
            )
            write("")
            withBlock("if (slf4jLevel != null) {", "}") {
                withBlock("#T(#T) {", "}", RuntimeTypes.KtorServerCore.install, RuntimeTypes.KtorServerLogging.CallLogging) {
                    write("level = slf4jLevel")
                    withBlock("format { call ->", "}") {
                        write("val status = call.response.status()")
                        write("\"\${call.request.#T.value} \${call.request.#T} → \$status\"", RuntimeTypes.KtorServerRouting.requestHttpMethod, RuntimeTypes.KtorServerRouting.requestUri)
                    }
                }
            }
            write("val log = #T.getLogger(#S)", RuntimeTypes.KtorLoggingSlf4j.LoggerFactory, ctx.settings.pkg.name)

            withBlock("monitor.subscribe(#T) {", "}", RuntimeTypes.KtorServerCore.ApplicationStarting) {
                write("log.info(#S)", "Server is starting...")
            }

            withBlock("monitor.subscribe(#T) {", "}", RuntimeTypes.KtorServerCore.ApplicationStarted) {
                write("log.info(#S)", "Server started – ready to accept requests.")
            }

            withBlock("monitor.subscribe(#T) {", "}", RuntimeTypes.KtorServerCore.ApplicationStopping) {
                write("log.warn(#S)", "Server is stopping – waiting for in-flight requests...")
            }

            withBlock("monitor.subscribe(#T) {", "}", RuntimeTypes.KtorServerCore.ApplicationStopped) {
                write("log.info(#S)", "Server stopped cleanly.")
            }
        }
    }
    val loggingWriter = LoggingWriter()
    loggingWriter.withBlock("<configuration>", "</configuration>") {
        withBlock("<appender name=#S class=#S>", "</appender>", "STDOUT", "ch.qos.logback.core.ConsoleAppender") {
            withBlock("<encoder>", "</encoder>") {
                withBlock("<pattern>", "</pattern>") {
                    write("%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5level %logger{36} - %msg%n")
                }
            }
        }
        withBlock("<root>", "</root>") {
            write("<appender-ref ref=#S/>", "STDOUT")
        }
    }
    val contents = loggingWriter.toString()
    fileManifest.writeFile("src/main/resources/logback.xml", contents)
}
