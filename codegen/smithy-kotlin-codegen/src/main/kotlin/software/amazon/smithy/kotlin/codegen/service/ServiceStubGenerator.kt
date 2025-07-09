package software.amazon.smithy.kotlin.codegen.service

import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.kotlin.codegen.core.GenerationContext
import software.amazon.smithy.kotlin.codegen.core.InlineCodeWriterFormatter
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.defaultName
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.core.withInlineBlock
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.HttpTrait
import software.amazon.smithy.protocol.traits.Rpcv2CborTrait
import software.amazon.smithy.utils.AbstractCodeWriter

class LoggingWriter(parent: LoggingWriter? = null) : AbstractCodeWriter<LoggingWriter>() {
    init {
        trimBlankLines(parent?.trimBlankLines ?: 1)
        trimTrailingSpaces(parent?.trimTrailingSpaces ?: true)
        indentText = parent?.indentText ?: "    "
        expressionStart = parent?.expressionStart ?: '#'
        putFormatter('W', InlineCodeWriterFormatter(::LoggingWriter))
    }
}

class ServiceStubGenerator(
    private val ctx: GenerationContext,
    private val delegator: KotlinDelegator,
    private val fileManifest: FileManifest,
) {
    private val serviceShape = ctx.settings.getService(ctx.model)

    private val operations: List<OperationShape> = TopDownIndex
        .of(ctx.model)
        .getContainedOperations(serviceShape)
        .sortedBy { it.defaultName() }

    fun render() {
        renderServiceFrameworkConfig()
        renderServiceFramework()
        renderPlugins()
        renderLogging()
        renderAuthModule()
        renderConstraintValidators()
        renderPerOperationHandlers()
        renderRouting()
        renderMainFile()
    }

    // Writes `Main.kt` that launch the server.
    private fun renderMainFile() {
        val portName = "port"
        val engineFactoryName = "engineFactory"
        val closeGracePeriodMillisName = "closeGracePeriodMillis"
        val closeTimeoutMillisName = "closeTimeoutMillis"
        val logLevelName = "logLevel"
        delegator.useFileWriter("Main.kt", ctx.settings.pkg.name) { writer ->
            writer.addImport("${ctx.settings.pkg.name}.configurations", "LogLevel")
            writer.addImport("${ctx.settings.pkg.name}.configurations", "ServiceEngine")
            writer.addImport("${ctx.settings.pkg.name}.configurations", "ServiceFrameworkConfig")
            writer.addImport("${ctx.settings.pkg.name}.configurations", "KTORServiceFramework")

            writer.withBlock("public fun main(args: Array<String>): Unit {", "}") {
                write("val argMap: Map<String, String> = args.asList().chunked(2).associate { (k, v) -> k.removePrefix(#S) to v }", "--")
                write("")
                withBlock("ServiceFrameworkConfig.init(", ")") {
                    write("port = argMap[#S]?.toInt() ?: 8080, ", portName)
                    write("engine = ServiceEngine.fromValue(argMap[#S] ?: ServiceEngine.NETTY.value), ", engineFactoryName)
                    write("closeGracePeriodMillis = argMap[#S]?.toLong() ?: 1000, ", closeGracePeriodMillisName)
                    write("closeTimeoutMillis = argMap[#S]?.toLong() ?: 5000, ", closeTimeoutMillisName)
                    write("logLevel = LogLevel.fromValue(argMap[#S] ?: LogLevel.INFO.value), ", logLevelName)
                }
                write("")
                write("val service = KTORServiceFramework()")
                write("service.start()")
            }
        }
    }

    // Writes `ServiceFramework.kt` that boots the embedded Ktor service.
    private fun renderServiceFramework() {
        delegator.useFileWriter("ServiceFramework.kt", "${ctx.settings.pkg.name}.configurations") { writer ->
            writer.addImport("${ctx.settings.pkg.name}.configurations", "ServiceFrameworkConfig")

            writer.withBlock("internal interface ServiceFramework: #T {", "}", RuntimeTypes.Core.IO.Closeable) {
                write("// start the service and begin accepting connections")
                write("public fun start()")
            }
                .write("")

            when (ctx.settings.serviceStub.framework) {
                ServiceFramework.KTOR -> renderKTORServiceFramework(writer)
            }
        }
    }

    private fun renderServiceFrameworkConfig() {
        delegator.useFileWriter("ServiceFrameworkConfig.kt", "${ctx.settings.pkg.name}.configurations") { writer ->
            writer.withBlock("internal enum class LogLevel(val value: String) {", "}") {
                write("INFO(#S),", "INFO")
                write("WARN(#S),", "WARN")
                write("DEBUG(#S),", "DEBUG")
                write("ERROR(#S),", "ERROR")
                write("TRACE(#S),", "TRACE")
                write("OFF(#S),", "OFF")
                write(";")
                write("")
                write("override fun toString(): String = value")
                write("")
                withBlock("companion object {", "}") {
                    withBlock("fun fromValue(value: String): LogLevel = when (value.uppercase()) {", "}") {
                        write("INFO.value -> INFO")
                        write("WARN.value -> WARN")
                        write("DEBUG.value -> DEBUG")
                        write("ERROR.value -> ERROR")
                        write("TRACE.value -> TRACE")
                        write("OFF.value -> OFF")
                        write("else -> throw IllegalArgumentException(#S)", "Unknown LogLevel value: \$value")
                    }
                }
            }
            writer.write("")

            writer.withBlock("internal enum class ServiceEngine(val value: String) {", "}") {
                write("NETTY(#S),", "netty")
                write(";")
                write("")
                write("override fun toString(): String = value")
                write("")
                withBlock("companion object {", "}") {
                    withBlock("fun fromValue(value: String): ServiceEngine = when (value.lowercase()) {", "}") {
                        write("NETTY.value -> NETTY")
                        write("else -> throw IllegalArgumentException(#S)", "\$value is not a valid ServerFramework value, expected \$NETTY")
                    }
                }
                write("")
                withBlock("fun toEngineFactory(): #T<*, *> {", "}", RuntimeTypes.KtorServerCore.ApplicationEngineFactory) {
                    withBlock("return when(this) {", "}") {
                        write("NETTY -> #T", RuntimeTypes.KtorServerNetty.Netty)
                    }
                }
            }
            writer.write("")

            writer.withBlock("internal object ServiceFrameworkConfig {", "}") {
                write("private var backing: Data? = null")
                write("")
                withBlock("private data class Data(", ")") {
                    write("val port: Int,")
                    write("val engine: ServiceEngine,")
                    write("val closeGracePeriodMillis: Long,")
                    write("val closeTimeoutMillis: Long,")
                    write("val logLevel: LogLevel,")
                }
                write("")
                write("val port: Int get() = backing?.port ?: notInitialised(#S)", "port")
                write("val engine: ServiceEngine get() = backing?.engine ?: notInitialised(#S)", "engine")
                write("val closeGracePeriodMillis: Long get() = backing?.closeGracePeriodMillis ?: notInitialised(#S)", "closeGracePeriodMillis")
                write("val closeTimeoutMillis: Long get() = backing?.closeTimeoutMillis ?: notInitialised(#S)", "closeTimeoutMillis")
                write("val logLevel: LogLevel get() = backing?.logLevel ?: notInitialised(#S)", "logLevel")
                write("")
                withInlineBlock("fun init(", ")") {
                    write("port: Int,")
                    write("engine: ServiceEngine,")
                    write("closeGracePeriodMillis: Long,")
                    write("closeTimeoutMillis: Long,")
                    write("logLevel: LogLevel,")
                }
                withBlock("{", "}") {
                    write("check(backing == null) { #S }", "ServiceFrameworkConfig has already been initialised")
                    write("backing = Data(port, engine, closeGracePeriodMillis, closeTimeoutMillis, logLevel)")
                }
                write("")
                withBlock("private fun notInitialised(prop: String): Nothing {", "}") {
                    write("error(#S)", "ServiceFrameworkConfig.\$prop accessed before init()")
                }
            }
        }
    }

    private fun renderKTORServiceFramework(writer: KotlinWriter) {
        writer.addImport(RuntimeTypes.KtorServerNetty.Netty)
        writer.addImport("${ctx.settings.pkg.name}.plugins", "configureErrorHandling")
        writer.addImport(ctx.settings.pkg.name, "configureRouting")
        writer.addImport(ctx.settings.pkg.name, "configureLogging")
        writer.addImport("${ctx.settings.pkg.name}.configurations", "ServiceFrameworkConfig")

        writer.withBlock("internal class KTORServiceFramework () : ServiceFramework {", "}") {
            write("private var engine: #T<*, *>? = null", RuntimeTypes.KtorServerCore.EmbeddedServerType)
            write("")
            withBlock("override fun start() {", "}") {
                withBlock(
                    "engine = #T(ServiceFrameworkConfig.engine.toEngineFactory(), port = ServiceFrameworkConfig.port) {",
                    "}.apply { start(wait = true) }",
                    RuntimeTypes.KtorServerCore.embeddedServer,
                ) {
                    write("configureErrorHandling()")
                    write("configureRouting()")
                    write("configureLogging()")
                }
            }
            write("")
            withBlock("final override fun close() {", "}") {
                write("engine?.stop(ServiceFrameworkConfig.closeGracePeriodMillis, ServiceFrameworkConfig.closeTimeoutMillis)")
                write("engine = null")
            }
        }
    }

    private fun renderLogging() {
        delegator.useFileWriter("Logging.kt", ctx.settings.pkg.name) { writer ->
            writer.addImport("${ctx.settings.pkg.name}.configurations", "LogLevel")
            writer.addImport("${ctx.settings.pkg.name}.configurations", "ServiceFrameworkConfig")

            writer.withBlock("internal fun #T.configureLogging() {", "}", RuntimeTypes.KtorServerCore.Application) {
                withBlock("val slf4jLevel: #T? = when (ServiceFrameworkConfig.logLevel) {", "}", RuntimeTypes.KtorLoggingSlf4j.Level) {
                    write("LogLevel.INFO -> #T.INFO", RuntimeTypes.KtorLoggingSlf4j.Level)
                    write("LogLevel.TRACE -> #T.TRACE", RuntimeTypes.KtorLoggingSlf4j.Level)
                    write("LogLevel.DEBUG -> #T.DEBUG", RuntimeTypes.KtorLoggingSlf4j.Level)
                    write("LogLevel.WARN -> #T.WARN", RuntimeTypes.KtorLoggingSlf4j.Level)
                    write("LogLevel.ERROR -> #T.ERROR", RuntimeTypes.KtorLoggingSlf4j.Level)
                    write("LogLevel.OFF -> null")
                    write("else -> #T.INFO", RuntimeTypes.KtorLoggingSlf4j.Level)
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

                withBlock("monitor.subscribe(#T) {", "}", RuntimeTypes.KtorServerCore.ApplicationStopping) {
                    write("log.warn(#S)", "▶ Server is stopping – waiting for in-flight requests...")
                }

                withBlock("monitor.subscribe(#T) {", "}", RuntimeTypes.KtorServerCore.ApplicationStopped) {
                    write("log.info(#S)", "⏹ Server stopped cleanly.")
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

    // Generates `Authentication.kt` with Authenticator interface + configureSecurity().
    private fun renderAuthModule() {
    }

    // For every operation request structure, create a `validate()` function file.
    private fun renderConstraintValidators() {
    }

    // Writes `Routing.kt` that maps Smithy operations → Ktor routes.
    private fun renderRouting() {
        val cborShapeIds = mutableListOf<ShapeId>()
        val protocolTrait = serviceShape.getTrait<Rpcv2CborTrait>()
        if (protocolTrait != null) {
            cborShapeIds.addAll(serviceShape.operations)
        }

        delegator.useFileWriter("Routing.kt", ctx.settings.pkg.name) { writer ->

            operations.forEach { shape ->
                writer.addImport("${ctx.settings.pkg.name}.serde", "${shape.id.name}OperationDeserializer")
                writer.addImport("${ctx.settings.pkg.name}.serde", "${shape.id.name}OperationSerializer")
                writer.addImport("${ctx.settings.pkg.name}.model", "${shape.id.name}Request")
                writer.addImport("${ctx.settings.pkg.name}.model", "${shape.id.name}Response")
                writer.addImport("${ctx.settings.pkg.name}.operations", "${shape.id.name}HandleRequest")
            }
            writer.addImport("${ctx.settings.pkg.name}.plugins", "ContentTypeGuard")

            writer.withBlock("internal fun #T.configureRouting(): Unit {", "}", RuntimeTypes.KtorServerCore.Application) {
                withBlock("#T {", "}", RuntimeTypes.KtorServerRouting.routing) {
                    withBlock("#T(#S) {", "}", RuntimeTypes.KtorServerRouting.get, "/") {
                        write(" #T.#T(#S)", RuntimeTypes.KtorServerCore.applicationCall, RuntimeTypes.KtorServerRouting.responseText, "hello world")
                    }
                    operations.filter { it.hasTrait(HttpTrait.ID) }
                        .forEach { shape ->
                            val httpTrait = shape.getTrait<HttpTrait>() ?: error("Http trait is missing")
                            val uri = httpTrait.uri ?: error("Http trait uri is missing")
                            val method = when (httpTrait.method) {
                                "GET" -> RuntimeTypes.KtorServerRouting.get
                                "POST" -> RuntimeTypes.KtorServerRouting.post
                                "PUT" -> RuntimeTypes.KtorServerRouting.put
                                "PATCH" -> RuntimeTypes.KtorServerRouting.patch
                                "DELETE" -> RuntimeTypes.KtorServerRouting.delete
                                "HEAD" -> RuntimeTypes.KtorServerRouting.head
                                "OPTIONS" -> RuntimeTypes.KtorServerRouting.options
                                else -> error("Unsupported http trait ${httpTrait.method}")
                            }
                            val contentType = if (shape.id in cborShapeIds) {
                                "cbor()"
                            } else {
                                error("Unsupported content type")
                            }
                            withBlock("#T (#S) {", "}", RuntimeTypes.KtorServerRouting.route, uri) {
                                write("#T(ContentTypeGuard) { $contentType }", RuntimeTypes.KtorServerCore.install)
                                withBlock("#T {", "}", method) {
                                    withInlineBlock("try {", "}") {
                                        write("val request = #T.#T<ByteArray>()", RuntimeTypes.KtorServerCore.applicationCall, RuntimeTypes.KtorServerRouting.requestReceive)
                                        write("val deserializer = ${shape.id.name}OperationDeserializer()")
                                        withBlock("val requestObj = try { deserializer.deserialize(#T(), request) } catch (ex: Exception) {", "}", RuntimeTypes.Core.ExecutionContext) {
                                            write("throw #T(#S, ex)", RuntimeTypes.KtorServerCore.BadRequestException, "Malformed CBOR input")
                                        }
                                        write("val responseObj = ${shape.id.name}HandleRequest(requestObj)")
                                        write("val serializer = ${shape.id.name}OperationSerializer()")
                                        withBlock("val response = try { serializer.serialize(#T(), responseObj) } catch (ex: Exception) {", "}", RuntimeTypes.Core.ExecutionContext) {
                                            write("throw #T(#S, ex)", RuntimeTypes.KtorServerCore.BadRequestException, "Malformed CBOR output")
                                        }
                                        withBlock("#T.#T(", ")", RuntimeTypes.KtorServerCore.applicationCall, RuntimeTypes.KtorServerRouting.responseRespondBytes) {
                                            write("bytes = response.body.#T() ?:  ByteArray(0),", RuntimeTypes.Http.readAll)
                                            write("contentType = #T,", RuntimeTypes.KtorServerHttp.Cbor)
                                            write("status = #T.OK,", RuntimeTypes.KtorServerHttp.HttpStatusCode)
                                        }
                                    }
                                    withBlock(" catch (t: Throwable) {", "}") {
                                        write("throw t")
                                    }
                                }
                            }
                        }
                }
            }
        }
    }

    private fun renderPlugins() {
        renderErrorHandler()
        renderContentTypeGuard()
    }

    private fun renderErrorHandler() {
        delegator.useFileWriter("ErrorHandler.kt", "${ctx.settings.pkg.name}.plugins") { writer ->

            writer.write("@#T", RuntimeTypes.KtorServerCborSerde.Serializable)
                .write("private data class ErrorPayload(val code: Int, val message: String)")
                .write("")
                .withInlineBlock("internal class ErrorEnvelope(", ")") {
                    write("val code: Int,")
                    write("val msg: String,")
                    write("cause: Throwable? = null,")
                }
                .withBlock(" : RuntimeException(msg, cause) {", "}") {
                    withBlock("fun toJson(json: #T = #T { }): String {", "}", RuntimeTypes.KtorServerJsonSerde.Json, RuntimeTypes.KtorServerJsonSerde.Json) {
                        withInlineBlock("return json.encodeToString(", ")") {
                            write("ErrorPayload(code, message ?: #S)", "Unknown error")
                        }
                    }
                    withBlock("fun toCbor(cbor: #T = #T { }): ByteArray {", "}", RuntimeTypes.KtorServerCborSerde.Cbor, RuntimeTypes.KtorServerCborSerde.Cbor) {
                        withInlineBlock("return cbor.#T(", ")", RuntimeTypes.KtorServerCborSerde.encodeToByteArray) {
                            write("ErrorPayload(code, message ?: #S)", "Unknown error")
                        }
                    }
                }
                .write("")
                .withBlock("internal fun #T.configureErrorHandling() {", "}", RuntimeTypes.KtorServerCore.Application) {
                    write("")
                    withBlock(
                        "#T(#T) {",
                        "}",
                        RuntimeTypes.KtorServerCore.install,
                        RuntimeTypes.KtorServerStatusPage.StatusPages,
                    ) {
                        withBlock("#T<Throwable> { call, cause ->", "}", RuntimeTypes.KtorServerStatusPage.exception) {
                            withBlock("val status = when (cause) {", "}") {
                                write(
                                    "is ErrorEnvelope -> #T.fromValue(cause.code)",
                                    RuntimeTypes.KtorServerHttp.HttpStatusCode,
                                )
                                write(
                                    "is #T -> #T.BadRequest",
                                    RuntimeTypes.KtorServerCore.BadRequestException,
                                    RuntimeTypes.KtorServerHttp.HttpStatusCode,
                                )
                                write("else -> #T.InternalServerError", RuntimeTypes.KtorServerHttp.HttpStatusCode)
                            }
                            write("")

                            withBlock("val contentType =", "") {
                                write(
                                    "if (call.request.#T().any { it.value == #S }) { #S }",
                                    RuntimeTypes.KtorServerRouting.requestacceptItems,
                                    "application/cbor",
                                    "cbor",
                                )
                                write(
                                    "else if (call.request.#T().any { it.value == #S }) { #S }",
                                    RuntimeTypes.KtorServerRouting.requestacceptItems,
                                    "application/json",
                                    "json",
                                )
                                write("else { #S }", "text")
                            }

                            write("val envelope = if (cause is ErrorEnvelope) cause else ErrorEnvelope(status.value, cause.message ?: #S)", "Unexpected error")
                            write("")
                            withBlock("when (contentType) {", "}") {
                                withBlock("#S -> {", "}", "cbor") {
                                    withBlock("call.#T(", ")", RuntimeTypes.KtorServerRouting.responseRespondBytes) {
                                        write("bytes = envelope.toCbor(),")
                                        write("status = status,")
                                        write("contentType = #T", RuntimeTypes.KtorServerHttp.Cbor)
                                    }
                                }
                                withBlock("#S -> {", "}", "json") {
                                    withBlock("call.#T(", ")", RuntimeTypes.KtorServerRouting.responseText) {
                                        write("envelope.toJson(),")
                                        write("status = status,")
                                        write("contentType = #T", RuntimeTypes.KtorServerHttp.Json)
                                    }
                                }
                                withBlock("#S -> {", "}", "text") {
                                    withBlock("call.#T(", ")", RuntimeTypes.KtorServerRouting.responseText) {
                                        write("envelope.msg,")
                                        write("status = status")
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun renderContentTypeGuard() {
        delegator.useFileWriter("ContentTypeGuard.kt", "${ctx.settings.pkg.name}.plugins") { writer ->
            writer.addImport("${ctx.settings.pkg.name}.plugins", "ErrorEnvelope")
            writer.withBlock("public class ContentTypeGuardConfig {", "}") {
                write("public var allow: List<#T> = emptyList()", RuntimeTypes.KtorServerHttp.ContentType)
                write("")
                withBlock("public fun json(): Unit {", "}") {
                    write("allow = listOf(#T)", RuntimeTypes.KtorServerHttp.Json)
                }
                write("")
                withBlock("public fun cbor(): Unit {", "}") {
                    write("allow = listOf(#T)", RuntimeTypes.KtorServerHttp.Cbor)
                }
            }
                .write("")

            writer.withInlineBlock(
                "public val ContentTypeGuard: #T<ContentTypeGuardConfig> = #T(",
                ")",
                RuntimeTypes.KtorServerCore.ApplicationRouteScopedPlugin,
                RuntimeTypes.KtorServerCore.ApplicationCreateRouteScopedPlugin,
            ) {
                write("name = #S,", "ContentTypeGuard")
                write("createConfiguration = ::ContentTypeGuardConfig,")
            }
                .withBlock("{", "}") {
                    write("val allowed: List<#T> = pluginConfig.allow", RuntimeTypes.KtorServerHttp.ContentType)
                    write("require(allowed.isNotEmpty()) { #S }", "ContentTypeGuard installed with empty allow-list.")
                    write("")
                    withBlock("onCall { call ->", "}") {
                        write("val incoming = call.request.#T()", RuntimeTypes.KtorServerRouting.requestContentType)
                        withBlock("if (incoming == #T.Any || allowed.none { incoming.match(it) }) {", "}", RuntimeTypes.KtorServerHttp.ContentType) {
                            withBlock("throw ErrorEnvelope(", ")") {
                                write("#T.UnsupportedMediaType.value, ", RuntimeTypes.KtorServerHttp.HttpStatusCode)
                                write("#S", "Allowed Content-Type(s): \${allowed.joinToString()}")
                            }
                        }
                    }
                }
        }
    }

    // Emits one stub handler per Smithy operation (`OperationNameHandler.kt`).
    private fun renderPerOperationHandlers() {
        operations.forEach { shape ->
            val name = shape.id.name
            delegator.useFileWriter("${name}Operation.kt", "${ctx.settings.pkg.name}.operations") { writer ->
                writer.addImport("${ctx.settings.pkg.name}.model", "${shape.id.name}Request")
                writer.addImport("${ctx.settings.pkg.name}.model", "${shape.id.name}Response")

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
