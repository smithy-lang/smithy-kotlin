package software.amazon.smithy.kotlin.codegen.service

import software.amazon.smithy.aws.traits.protocols.RestJson1Trait
import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.kotlin.codegen.core.GenerationContext
import software.amazon.smithy.kotlin.codegen.core.InlineCodeWriterFormatter
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.core.withInlineBlock
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.model.traits.AuthTrait
import software.amazon.smithy.model.traits.HttpBearerAuthTrait
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

internal class KtorStubGenerator(
    ctx: GenerationContext,
    delegator: KotlinDelegator,
    fileManifest: FileManifest,
) : AbstractStubGenerator(ctx, delegator, fileManifest) {
    val pkgName = ctx.settings.pkg.name

    // Writes `Main.kt` that launches the server.
    override fun renderMainFile() {
        val portName = "port"
        val engineFactoryName = "engineFactory"
        val closeGracePeriodMillisName = "closeGracePeriodMillis"
        val closeTimeoutMillisName = "closeTimeoutMillis"
        val logLevelName = "logLevel"
        delegator.useFileWriter("Main.kt", ctx.settings.pkg.name) { writer ->

            writer.withBlock("public fun main(args: Array<String>): Unit {", "}") {
                write("val argMap: Map<String, String> = args.asList().chunked(2).associate { (k, v) -> k.removePrefix(#S) to v }", "--")
                write("")
                write("val defaultPort = 8080")
                write("val defaultEngine = #T.NETTY.value", ServiceTypes(pkgName).serviceEngine)
                write("val defaultCloseGracePeriodMillis = 1_000L")
                write("val defaultCloseTimeoutMillis = 5_000L")
                write("val defaultLogLevel = #T.INFO.value", ServiceTypes(pkgName).logLevel)
                write("")
                withBlock("#T.init(", ")", ServiceTypes(pkgName).serviceFrameworkConfig) {
                    write("port = argMap[#S]?.toInt() ?: defaultPort, ", portName)
                    write("engine = #T.fromValue(argMap[#S] ?: defaultEngine), ", ServiceTypes(pkgName).serviceEngine, engineFactoryName)
                    write("closeGracePeriodMillis = argMap[#S]?.toLong() ?: defaultCloseGracePeriodMillis, ", closeGracePeriodMillisName)
                    write("closeTimeoutMillis = argMap[#S]?.toLong() ?: defaultCloseTimeoutMillis, ", closeTimeoutMillisName)
                    write("logLevel = #T.fromValue(argMap[#S] ?: defaultLogLevel), ", ServiceTypes(pkgName).logLevel, logLevelName)
                }
                write("")
                when (ctx.settings.serviceStub.framework) {
                    ServiceFramework.KTOR -> write("val service = #T()", ServiceTypes(pkgName).ktorServiceFramework)
                }
                write("service.start()")
            }
        }
    }

    // Writes `ServiceFramework.kt` that boots the embedded Ktor service.
    override fun renderServiceFramework() {
        delegator.useFileWriter("ServiceFramework.kt", "${ctx.settings.pkg.name}.framework") { writer ->

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

    override fun renderServiceFrameworkConfig() {
        delegator.useFileWriter("ServiceFrameworkConfig.kt", "${ctx.settings.pkg.name}.config") { writer ->
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
                    withBlock("fun fromValue(value: String): #T = when (value.uppercase()) {", "}", ServiceTypes(pkgName).logLevel) {
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
                    withBlock("fun fromValue(value: String): #T = when (value.lowercase()) {", "}", ServiceTypes(pkgName).serviceEngine) {
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
                    write("val engine: #T,", ServiceTypes(pkgName).serviceEngine)
                    write("val closeGracePeriodMillis: Long,")
                    write("val closeTimeoutMillis: Long,")
                    write("val logLevel: #T,", ServiceTypes(pkgName).logLevel)
                }
                write("")
                write("val port: Int get() = backing?.port ?: notInitialised(#S)", "port")
                write("val engine: #T get() = backing?.engine ?: notInitialised(#S)", ServiceTypes(pkgName).serviceEngine, "engine")
                write("val closeGracePeriodMillis: Long get() = backing?.closeGracePeriodMillis ?: notInitialised(#S)", "closeGracePeriodMillis")
                write("val closeTimeoutMillis: Long get() = backing?.closeTimeoutMillis ?: notInitialised(#S)", "closeTimeoutMillis")
                write("val logLevel: #T get() = backing?.logLevel ?: notInitialised(#S)", ServiceTypes(pkgName).logLevel, "logLevel")
                write("")
                withInlineBlock("fun init(", ")") {
                    write("port: Int,")
                    write("engine: #T,", ServiceTypes(pkgName).serviceEngine)
                    write("closeGracePeriodMillis: Long,")
                    write("closeTimeoutMillis: Long,")
                    write("logLevel: #T,", ServiceTypes(pkgName).logLevel)
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

        writer.withBlock("internal class KTORServiceFramework () : ServiceFramework {", "}") {
            write("private var engine: #T<*, *>? = null", RuntimeTypes.KtorServerCore.EmbeddedServerType)
            write("")
            withBlock("override fun start() {", "}") {
                withBlock(
                    "engine = #T(#T.engine.toEngineFactory(), port = #T.port) {",
                    "}.apply { start(wait = true) }",
                    RuntimeTypes.KtorServerCore.embeddedServer,
                    ServiceTypes(pkgName).serviceFrameworkConfig,
                    ServiceTypes(pkgName).serviceFrameworkConfig,
                ) {
                    write("#T()", ServiceTypes(pkgName).configureErrorHandling)

                    write("#T()", ServiceTypes(pkgName).configureRouting)
                    write("#T()", ServiceTypes(pkgName).configureLogging)
                }
            }
            write("")
            withBlock("final override fun close() {", "}") {
                write("engine?.stop(#T.closeGracePeriodMillis, #T.closeTimeoutMillis)", ServiceTypes(pkgName).serviceFrameworkConfig, ServiceTypes(pkgName).serviceFrameworkConfig)
                write("engine = null")
            }
        }
    }

    override fun renderLogging() {
        delegator.useFileWriter("Logging.kt", "${ctx.settings.pkg.name}.utils") { writer ->

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
    override fun renderAuthModule() {
    }

    // For every operation request structure, create a `validate()` function file.
    override fun renderConstraintValidators() {
    }

    // Writes `Routing.kt` that maps Smithy operations → Ktor routes.
    override fun renderRouting() {
        val isCborProtocolTrait = (serviceShape.getTrait<Rpcv2CborTrait>() != null)
        val isJsonProtocolTrait = (serviceShape.getTrait<RestJson1Trait>() != null)

        delegator.useFileWriter("Routing.kt", ctx.settings.pkg.name) { writer ->

            operations.forEach { shape ->
                writer.addImport("${ctx.settings.pkg.name}.serde", "${shape.id.name}OperationDeserializer")
                writer.addImport("${ctx.settings.pkg.name}.serde", "${shape.id.name}OperationSerializer")
                writer.addImport("${ctx.settings.pkg.name}.model", "${shape.id.name}Request")
                writer.addImport("${ctx.settings.pkg.name}.model", "${shape.id.name}Response")
                writer.addImport("${ctx.settings.pkg.name}.operations", "handle${shape.id.name}Request")
            }

            writer.withBlock("internal fun #T.configureRouting(): Unit {", "}", RuntimeTypes.KtorServerCore.Application) {
                withBlock("#T {", "}", RuntimeTypes.KtorServerRouting.routing) {
                    withBlock("#T(#S) {", "}", RuntimeTypes.KtorServerRouting.get, "/") {
                        write(" #T.#T(#S)", RuntimeTypes.KtorServerCore.applicationCall, RuntimeTypes.KtorServerRouting.responseText, "hello world")
                    }

                    val hasServiceHttpBearerAuthTrait = serviceShape.getTrait<HttpBearerAuthTrait>() != null
                    operations.filter { it.hasTrait(HttpTrait.ID) }
                        .forEach { shape ->
                            val httpTrait = shape.getTrait<HttpTrait>()!!

                            val uri = httpTrait.uri
                            val successCode = httpTrait.code
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

                            val authTrait = shape.getTrait<AuthTrait>()
                            val hasOperationBearerAuthTrait = authTrait?.valueSet?.contains(HttpBearerAuthTrait.ID) ?: true

                            val contentType = if (isCborProtocolTrait) {
                                "cbor()"
                            } else if (isJsonProtocolTrait) {
                                "json()"
                            } else {
                                error("Unsupported content type")
                            }

                            withBlock("#T (#S) {", "}", RuntimeTypes.KtorServerRouting.route, uri) {
                                write("#T(#T) { $contentType }", RuntimeTypes.KtorServerCore.install, ServiceTypes(pkgName).contentTypeGuard)

                                withBlock("#T {", "}", method) {
                                    withInlineBlock("try {", "}") {
                                        write(
                                            "val request = #T.#T<ByteArray>()",
                                            RuntimeTypes.KtorServerCore.applicationCall,
                                            RuntimeTypes.KtorServerRouting.requestReceive,
                                        )
                                        write("val deserializer = ${shape.id.name}OperationDeserializer()")
                                        withBlock(
                                            "val requestObj = try { deserializer.deserialize(#T(), request) } catch (ex: Exception) {",
                                            "}",
                                            RuntimeTypes.Core.ExecutionContext,
                                        ) {
                                            write(
                                                "throw #T(#S, ex)",
                                                RuntimeTypes.KtorServerCore.BadRequestException,
                                                "Malformed CBOR input",
                                            )
                                        }
                                        write("val responseObj = handle${shape.id.name}Request(requestObj)")
                                        write("val serializer = ${shape.id.name}OperationSerializer()")
                                        withBlock(
                                            "val response = try { serializer.serialize(#T(), responseObj) } catch (ex: Exception) {",
                                            "}",
                                            RuntimeTypes.Core.ExecutionContext,
                                        ) {
                                            write(
                                                "throw #T(#S, ex)",
                                                RuntimeTypes.KtorServerCore.BadRequestException,
                                                "Malformed CBOR output",
                                            )
                                        }
                                        withBlock(
                                            "#T.#T(",
                                            ")",
                                            RuntimeTypes.KtorServerCore.applicationCall,
                                            RuntimeTypes.KtorServerRouting.responseRespondBytes,
                                        ) {
                                            write(
                                                "bytes = response.body.#T() ?:  ByteArray(0),",
                                                RuntimeTypes.Http.readAll,
                                            )
                                            write("contentType = #T,", RuntimeTypes.KtorServerHttp.Cbor)
                                            write(
                                                "status = #T.fromValue($successCode),",
                                                RuntimeTypes.KtorServerHttp.HttpStatusCode,
                                            )
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

    override fun renderPlugins() {
        renderErrorHandler()
        renderContentTypeGuard()
    }

    private fun renderErrorHandler() {
        delegator.useFileWriter("ErrorHandler.kt", "${ctx.settings.pkg.name}.plugins") { writer ->
            writer.write("@#T", RuntimeTypes.KotlinxCborSerde.Serializable)
                .write("private data class ErrorPayload(val code: Int, val message: String)")
                .write("")
                .withInlineBlock("internal class ErrorEnvelope(", ")") {
                    write("val code: Int,")
                    write("val msg: String,")
                    write("cause: Throwable? = null,")
                }
                .withBlock(" : RuntimeException(msg, cause) {", "}") {
                    withBlock("fun toJson(json: #T = #T): String {", "}", RuntimeTypes.KotlinxJsonSerde.Json, RuntimeTypes.KotlinxJsonSerde.Json) {
                        withInlineBlock("return json.encodeToString(", ")") {
                            write("ErrorPayload(code, message ?: #S)", "Unknown error")
                        }
                    }
                    withBlock("fun toCbor(cbor: #T = #T { }): ByteArray {", "}", RuntimeTypes.KotlinxCborSerde.Cbor, RuntimeTypes.KotlinxCborSerde.Cbor) {
                        withInlineBlock("return cbor.#T(", ")", RuntimeTypes.KotlinxCborSerde.encodeToByteArray) {
                            write("ErrorPayload(code, message ?: #S)", "Unknown error")
                        }
                    }
                }
                .write("")
                .withInlineBlock("private suspend fun #T.respondEnvelope(", ")", RuntimeTypes.KtorServerCore.ApplicationCallClass) {
                    write("envelope: ErrorEnvelope,")
                    write("status: #T,", RuntimeTypes.KtorServerHttp.HttpStatusCode)
                }
                .withBlock("{", "}") {
                    write("val acceptsCbor = request.#T().any { it.value == #S }", RuntimeTypes.KtorServerRouting.requestacceptItems, "application/cbor")
                    write("val acceptsJson = request.#T().any { it.value == #S }", RuntimeTypes.KtorServerRouting.requestacceptItems, "application/json")

                    write("")
                    withBlock("when {", "}") {
                        withBlock("acceptsCbor -> {", "}") {
                            withBlock("#T(", ")", RuntimeTypes.KtorServerRouting.responseRespondBytes) {
                                write("bytes = envelope.toCbor(),")
                                write("status = status,")
                                write("contentType = #T", RuntimeTypes.KtorServerHttp.Cbor)
                            }
                        }
                        withBlock("acceptsJson -> {", "}") {
                            withBlock("#T(", ")", RuntimeTypes.KtorServerRouting.responseText) {
                                write("envelope.toJson(),")
                                write("status = status,")
                                write("contentType = #T", RuntimeTypes.KtorServerHttp.Json)
                            }
                        }
                        withBlock("else -> {", "}") {
                            withBlock("#T(", ")", RuntimeTypes.KtorServerRouting.responseText) {
                                write("envelope.msg,")
                                write("status = status")
                            }
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
                        withBlock("status(#T.Unauthorized) { call, status ->", "}", RuntimeTypes.KtorServerHttp.HttpStatusCode) {
                            write("val missing = call.request.headers[#S].isNullOrBlank()", "Authorization")
                            write("val message = if (missing) #S else #S", "Missing bearer token", "Invalid or expired bearer token")
                            write("call.respondEnvelope( ErrorEnvelope(#T.Unauthorized.value, message), #T.Unauthorized )", RuntimeTypes.KtorServerHttp.HttpStatusCode, RuntimeTypes.KtorServerHttp.HttpStatusCode)
                        }
                        write("")
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

                            write("val envelope = if (cause is ErrorEnvelope) cause else ErrorEnvelope(status.value, cause.message ?: #S)", "Unexpected error")
                            write("call.respondEnvelope( envelope, status )")
                        }
                    }
                }
        }
    }

    private fun renderContentTypeGuard() {
        delegator.useFileWriter("ContentTypeGuard.kt", "${ctx.settings.pkg.name}.plugins") { writer ->

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
                            withBlock("throw #T(", ")", ServiceTypes(pkgName).errorEnvelope) {
                                write("#T.UnsupportedMediaType.value, ", RuntimeTypes.KtorServerHttp.HttpStatusCode)
                                write("#S", "Allowed Content-Type(s): \${allowed.joinToString()}")
                            }
                        }
                    }
                }
        }
    }

    // Emits one stub handler per Smithy operation (`OperationNameHandler.kt`).
    override fun renderPerOperationHandlers() {
        operations.forEach { shape ->
            val name = shape.id.name
            delegator.useFileWriter("${name}Operation.kt", "${ctx.settings.pkg.name}.operations") { writer ->
                writer.addImport("${ctx.settings.pkg.name}.model", "${shape.id.name}Request")
                writer.addImport("${ctx.settings.pkg.name}.model", "${shape.id.name}Response")

                writer.withBlock("public fun handle${name}Request(req: ${name}Request): ${name}Response {", "}") {
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
