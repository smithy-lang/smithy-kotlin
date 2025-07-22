package software.amazon.smithy.kotlin.codegen.service

import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.kotlin.codegen.core.GenerationContext
import software.amazon.smithy.kotlin.codegen.core.InlineCodeWriterFormatter
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.core.withInlineBlock
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.traits.AuthTrait
import software.amazon.smithy.model.traits.HttpBearerAuthTrait
import software.amazon.smithy.model.traits.HttpTrait
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

    override fun renderServerFrameworkImplementation(writer: KotlinWriter) {
        writer.withBlock("internal fun #T.module(): Unit {", "}", RuntimeTypes.KtorServerCore.Application) {
            withBlock("#T(#T) {", "}", RuntimeTypes.KtorServerCore.install, RuntimeTypes.KtorServerBodyLimit.RequestBodyLimit) {
                write("bodyLimit { #T.requestBodyLimit }", ServiceTypes(pkgName).serviceFrameworkConfig)
            }
            write("#T()", ServiceTypes(pkgName).configureErrorHandling)
            write("#T()", ServiceTypes(pkgName).configureAuthentication)
            write("#T()", ServiceTypes(pkgName).configureRouting)
            write("#T()", ServiceTypes(pkgName).configureLogging)
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

    override fun renderUtils() {
        renderLogging()
    }

    private fun renderLogging() {
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

    // Generates `Authentication.kt` with Authenticator interface + configureSecurity().
    override fun renderAuthModule() {
        delegator.useFileWriter("UserPrincipal.kt", "${ctx.settings.pkg.name}.auth") { writer ->
            writer.withBlock("public data class UserPrincipal(", ")") {
                write("val user: String")
            }
        }

        delegator.useFileWriter("Validation.kt", "${ctx.settings.pkg.name}.auth") { writer ->
            writer.withBlock("public fun bearerValidation(token: String): UserPrincipal? {", "}") {
                write("// TODO: implement me")
                write("if (true) return UserPrincipal(#S) else return null", "Authenticated User")
            }
        }

        delegator.useFileWriter("Authentication.kt", "${ctx.settings.pkg.name}.auth") { writer ->
            writer.withBlock("internal fun #T.configureAuthentication() {", "}", RuntimeTypes.KtorServerCore.Application) {
                write("")
                withBlock(
                    "#T(#T) {",
                    "}",
                    RuntimeTypes.KtorServerCore.install,
                    RuntimeTypes.KtorServerAuth.Authentication,
                ) {
                    withBlock("#T(#S) {", "}", RuntimeTypes.KtorServerAuth.bearer, "auth-bearer") {
                        write("realm = #S", "Access to API")
                        write("authenticate { cred -> bearerValidation(cred.token) }")
                    }
                    write("provider(#S) { authenticate { ctx -> ctx.principal(Unit) } }", "no-auth")
                }
            }
        }
    }

    // For every operation request structure, create a `validate()` function file.
    override fun renderConstraintValidators() {
    }

    // Writes `Routing.kt` that maps Smithy operations → Ktor routes.
    override fun renderRouting() {
        val contentType = ContentType.fromServiceShape(serviceShape)

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
                        write(" #T.#T(#S)", RuntimeTypes.KtorServerCore.applicationCall, RuntimeTypes.KtorServerRouting.responseResponseText, "hello world")
                    }
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

                            val contentTypeGuard = when (contentType) {
                                ContentType.CBOR -> "cbor()"
                                ContentType.JSON -> "json()"
                            }

                            withBlock("#T (#S) {", "}", RuntimeTypes.KtorServerRouting.route, uri) {
                                write("#T(#T) { $contentTypeGuard }", RuntimeTypes.KtorServerCore.install, ServiceTypes(pkgName).contentTypeGuard)
                                write("#T(#T) { $contentTypeGuard }", RuntimeTypes.KtorServerCore.install, ServiceTypes(pkgName).acceptTypeGuard)
                                withBlock(
                                    "#W",
                                    "}",
                                    { w: KotlinWriter -> renderRoutingAuth(w, shape) },
                                ) {
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
                                                .call { renderResponseCall(writer, contentType, successCode) }
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
    }

    private fun renderRoutingAuth(w: KotlinWriter, shape: OperationShape) {
        val hasServiceHttpBearerAuthTrait = serviceShape.hasTrait(HttpBearerAuthTrait.ID)
        val authTrait = shape.getTrait<AuthTrait>()
        val hasOperationBearerAuthTrait = authTrait?.valueSet?.contains(HttpBearerAuthTrait.ID) ?: true

        if (hasServiceHttpBearerAuthTrait && hasOperationBearerAuthTrait) {
            w.write(
                "#T(#S) {",
                RuntimeTypes.KtorServerAuth.authenticate,
                "auth-bearer",
            )
        } else {
            w.write("#T(#S) {", RuntimeTypes.KtorServerAuth.authenticate, "no-auth")
        }
    }

    private fun renderResponseCall(
        w: KotlinWriter,
        contentType: ContentType,
        successCode: Int,
    ) {
        when (contentType) {
            ContentType.CBOR -> w.withBlock(
                "#T.#T(",
                ")",
                RuntimeTypes.KtorServerCore.applicationCall,
                RuntimeTypes.KtorServerRouting.responseRespondBytes,
            ) {
                write("bytes = response,")
                write("contentType = #T,", RuntimeTypes.KtorServerHttp.Cbor)
                write(
                    "status = #T.fromValue($successCode),",
                    RuntimeTypes.KtorServerHttp.HttpStatusCode,
                )
            }
            ContentType.JSON -> w.withBlock(
                "#T.#T(",
                ")",
                RuntimeTypes.KtorServerCore.applicationCall,
                RuntimeTypes.KtorServerRouting.responseResponseText,
            ) {
                write("text = response,")
                write("contentType = #T,", RuntimeTypes.KtorServerHttp.Json)
                write(
                    "status = #T.fromValue($successCode),",
                    RuntimeTypes.KtorServerHttp.HttpStatusCode,
                )
            }
        }
    }

    override fun renderPlugins() {
        renderErrorHandler()
        renderContentTypeGuard()
        renderAcceptTypeGuard()
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
                    write("val acceptsCbor = request.#T().any { it.value == #S }", RuntimeTypes.KtorServerRouting.requestAcceptItems, "application/cbor")
                    write("val acceptsJson = request.#T().any { it.value == #S }", RuntimeTypes.KtorServerRouting.requestAcceptItems, "application/json")
                    write("")
                    write("val log = #T.getLogger(#S)", RuntimeTypes.KtorLoggingSlf4j.LoggerFactory, ctx.settings.pkg.name)
                    write("log.info(#S)", "Route Error Message: \${envelope.msg}")
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
                            withBlock("#T(", ")", RuntimeTypes.KtorServerRouting.responseResponseText) {
                                write("envelope.toJson(),")
                                write("status = status,")
                                write("contentType = #T", RuntimeTypes.KtorServerHttp.Json)
                            }
                        }
                        withBlock("else -> {", "}") {
                            withBlock("#T(", ")", RuntimeTypes.KtorServerRouting.responseResponseText) {
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
                            write("call.respondEnvelope( ErrorEnvelope(status.value, message), status )")
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
                                write(
                                    "is #T -> #T.PayloadTooLarge",
                                    RuntimeTypes.KtorServerBodyLimit.PayloadTooLargeException,
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

            writer.withBlock("private fun #T.hasBody(): Boolean {", "}", RuntimeTypes.KtorServerRouting.requestApplicationRequest) {
                write(
                    "return (#T()?.let { it > 0 } == true) || headers.contains(#T.TransferEncoding)",
                    RuntimeTypes.KtorServerRouting.requestContentLength,
                    RuntimeTypes.KtorServerHttp.HttpHeaders,
                )
            }
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
                        write("if (!call.request.hasBody()) return@onCall")
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

    private fun renderAcceptTypeGuard() {
        delegator.useFileWriter("AcceptTypeGuard.kt", "${ctx.settings.pkg.name}.plugins") { writer ->

            writer.withBlock(
                "private fun #T.acceptedContentTypes(): List<#T> {",
                "}",
                RuntimeTypes.KtorServerRouting.requestApplicationRequest,
                RuntimeTypes.KtorServerHttp.ContentType,
            ) {
                write("val raw = headers[#T.Accept] ?: return emptyList()", RuntimeTypes.KtorServerHttp.HttpHeaders)
                write(
                    "return #T(raw).mapNotNull { it.value?.let(#T::parse) }",
                    RuntimeTypes.KtorServerHttp.parseAndSortHeader,
                    RuntimeTypes.KtorServerHttp.ContentType,
                )
            }

            writer.withBlock("public class AcceptTypeGuardConfig {", "}") {
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
                "public val AcceptTypeGuard: #T<AcceptTypeGuardConfig> = #T(",
                ")",
                RuntimeTypes.KtorServerCore.ApplicationRouteScopedPlugin,
                RuntimeTypes.KtorServerCore.ApplicationCreateRouteScopedPlugin,
            ) {
                write("name = #S,", "AcceptTypeGuard")
                write("createConfiguration = ::AcceptTypeGuardConfig,")
            }
                .withBlock("{", "}") {
                    write("val allowed: List<#T> = pluginConfig.allow", RuntimeTypes.KtorServerHttp.ContentType)
                    write("require(allowed.isNotEmpty()) { #S }", "AcceptTypeGuard installed with empty allow-list.")
                    write("")
                    withBlock("onCall { call ->", "}") {
                        write("val accepted = call.request.acceptedContentTypes()")
                        write("if (accepted.isEmpty()) return@onCall")
                        write("")
                        write("val isOk = accepted.any { candidate -> allowed.any { candidate.match(it) } }")

                        withBlock("if (!isOk) {", "}") {
                            withBlock("throw #T(", ")", ServiceTypes(pkgName).errorEnvelope) {
                                write("#T.NotAcceptable.value, ", RuntimeTypes.KtorServerHttp.HttpStatusCode)
                                write("#S", "Supported Accept(s): \${allowed.joinToString()}")
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
