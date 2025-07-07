package software.amazon.smithy.kotlin.codegen.service

import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.InlineCodeWriterFormatter
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.core.KotlinDependency
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.ShapeType
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
    private val settings: KotlinSettings,
    private val delegator: KotlinDelegator,
    private val fileManifest: FileManifest,
    private val shapes: Set<Shape>,
) {

    fun render() {
        // FIXME: check server framework here and render according to the chosen server framework
        renderMainFile()
        renderServiceFramework()
        renderPlugins()
        renderLogging()
        renderProtocolModule()
        renderAuthModule()
        renderConstraintValidators()
        renderRouting()
        renderPerOperationHandlers()
    }

    // Writes `Main.kt` that launch the server.
    private fun renderMainFile() {
        delegator.useFileWriter("Main.kt", settings.pkg.name) { writer ->
            writer.dependencies.addAll(KotlinDependency.KTOR_LOGGING_BACKEND.dependencies)
            writer.addImport("com.example.server.configurations", "KTORServiceFramework")

            writer.withBlock("public fun main(): Unit {", "}") {
                write("val service = KTORServiceFramework()")
                write("service.start()")
            }
        }
    }

    // Writes `ServiceFramework.kt` that boots the embedded Ktor service.
    private fun renderServiceFramework() {
        val port = settings.serviceStub.port
        val engine = when (settings.serviceStub.engine) {
            ServiceEngine.NETTY -> RuntimeTypes.KtorServerNetty.Netty
        }
        val closeGracePeriodMillis = 1000
        val closeTimeoutMillis = 5000

        val serviceFrameworkConfigName = "serviceFrameworkConfig"

        renderServiceFrameworkConfig()

        delegator.useFileWriter("ServiceFramework.kt", "${settings.pkg.name}.configurations") { writer ->
            writer.dependencies.addAll(KotlinDependency.KTOR_LOGGING_BACKEND.dependencies)
            writer.addImport("${settings.pkg.name}.configurations", "ServiceFrameworkConfig")

            writer
                .write("internal val $serviceFrameworkConfigName: ServiceFrameworkConfig = ServiceFrameworkConfig(port= $port, engineFactory = $engine, closeGracePeriodMillis = $closeGracePeriodMillis, closeTimeoutMillis = $closeTimeoutMillis)")
                .write("")
                .withBlock("public interface ServiceFramework: #T {", "}", RuntimeTypes.Core.IO.Closeable) {
                    write("// start the service and begin accepting connections")
                    write("public fun start()")
                }
                .write("")

            when (settings.serviceStub.framework) {
                ServiceFramework.KTOR -> renderKTORServiceFramework(writer, serviceFrameworkConfigName)
            }
        }
    }

    private fun renderServiceFrameworkConfig() {
        delegator.useFileWriter("ServiceFrameworkConfig.kt", "${settings.pkg.name}.configurations") { writer ->
            writer.withBlock("internal data class ServiceFrameworkConfig (", ")") {
                write("val port: Int,")
                write("val engineFactory: #T<*, *>,", RuntimeTypes.KtorServerCore.ApplicationEngineFactory)
                write("val closeGracePeriodMillis: Long,")
                write("val closeTimeoutMillis: Long,")
            }
        }
    }

    private fun renderKTORServiceFramework(writer: KotlinWriter, serviceFrameworkConfigName: String) {
        writer.addImport(RuntimeTypes.KtorServerNetty.Netty)
        writer.addImport(settings.pkg.name, "configureRouting")
        writer.addImport(settings.pkg.name, "configureLogging")

        writer.withBlock("internal class KTORServiceFramework : ServiceFramework {", "}") {
            write("private var engine: #T<*, *>? = null", RuntimeTypes.KtorServerCore.EmbeddedServerType)
            write("")
            withBlock("override fun start() {", "}") {
                withBlock(
                    "engine = #T($serviceFrameworkConfigName.engineFactory, port = $serviceFrameworkConfigName.port) {",
                    "}.apply { start(wait = true) }",
                    RuntimeTypes.KtorServerCore.embeddedServer,
                ) {
                    write("configureRouting()")
                    write("configureLogging()")
                }
            }
            write("")
            withBlock("final override fun close() {", "}") {
                write("engine?.stop($serviceFrameworkConfigName.closeGracePeriodMillis, $serviceFrameworkConfigName.closeTimeoutMillis)")
                write("engine = null")
            }
        }
    }

    private fun renderLogging() {
        delegator.useFileWriter("Logging.kt", settings.pkg.name) { writer ->

            writer.withBlock("internal fun #T.configureLogging() {", "}", RuntimeTypes.KtorServerCore.Application) {
                withBlock("#T(#T) {", "}", RuntimeTypes.KtorServerCore.install, RuntimeTypes.KtorServerLogging.CallLogging) {
                    write("level = #T.INFO", RuntimeTypes.KtorLoggingBackend.Level)
                    withBlock("format { call ->", "}") {
                        write("val status = call.response.status()")
                        write("\"\${call.request.#T.value} \${call.request.#T} → \$status\"", RuntimeTypes.KtorServerRouting.requestHttpMethod, RuntimeTypes.KtorServerRouting.requestUri)
                    }
                }
                write("val log = #T.getLogger(#S)", RuntimeTypes.KtorLoggingBackend.LoggerFactory, settings.pkg.name)

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
            renderLoggingLevel(loggingWriter)
        }
        val contents = loggingWriter.toString()
        fileManifest.writeFile("src/main/resources/logback.xml", contents)
    }

    private fun renderLoggingLevel(w: LoggingWriter) {
        val loggingLevel = settings.serviceStub.logLevel
        when (loggingLevel) {
            LogLevel.OFF ->
                w.write("<root level=#S>", "OFF")
            else ->
                w.withBlock("<root level=#S>", "</root>", loggingLevel.value.uppercase()) {
                    write("<appender-ref ref=#S/>", "STDOUT")
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
        val serviceShapes = shapes.filter { it.type == ShapeType.SERVICE }.map { it as ServiceShape }
        val cborShapeIds = mutableListOf<ShapeId>()
        serviceShapes.forEach { shape ->
            val protocolTrait = shape.getTrait<Rpcv2CborTrait>()
            if (protocolTrait != null) {
                cborShapeIds.addAll(shape.operations)
            }
        }
        val operationShapes = shapes.filter { it.type == ShapeType.OPERATION }.map { it as OperationShape }
        delegator.useFileWriter("Routing.kt", settings.pkg.name) { writer ->

            operationShapes.forEach { shape ->
                writer.addImport("${settings.pkg.name}.serde", "${shape.id.name}OperationDeserializer")
                writer.addImport("${settings.pkg.name}.serde", "${shape.id.name}OperationSerializer")
                writer.addImport("${settings.pkg.name}.model", "${shape.id.name}Request")
                writer.addImport("${settings.pkg.name}.model", "${shape.id.name}Response")
                writer.addImport("${settings.pkg.name}.operations", "${shape.id.name}HandleRequest")
            }
            writer.addImport("${settings.pkg.name}.plugins", "ContentTypeGuard")

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
                                    write("val request = #T.#T<ByteArray>()", RuntimeTypes.KtorServerCore.applicationCall, RuntimeTypes.KtorServerRouting.requestReceive)
                                    write("val deserializer = ${shape.id.name}OperationDeserializer()")
                                    write("val requestObj = deserializer.deserialize(#T(), request)", RuntimeTypes.Core.ExecutionContext)
                                    write("val responseObj = ${shape.id.name}HandleRequest(requestObj)")
                                    write("val serializer = ${shape.id.name}OperationSerializer()")
                                    write("val response = serializer.serialize(#T(), responseObj)", RuntimeTypes.Core.ExecutionContext)
                                    withBlock("#T.#T(", ")", RuntimeTypes.KtorServerCore.applicationCall, RuntimeTypes.KtorServerRouting.responseRespondBytes) {
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
    }

    private fun renderPlugins() {
        renderContentTypeGuard()
    }

    private fun renderContentTypeGuard() {
        delegator.useFileWriter("ContentTypeGuard.kt", "${settings.pkg.name}.plugins") { writer ->
            writer.withBlock("public class ContentTypeGuardConfig {", "}") {
                write("public var allow: List<#T> = emptyList()", RuntimeTypes.KtorServerHTTP.ContentType)
                write("")
                withBlock("public fun json(): Unit {", "}") {
                    write("allow = listOf(#T)", RuntimeTypes.KtorServerHTTP.Json)
                }
                write("")
                withBlock("public fun cbor(): Unit {", "}") {
                    write("allow = listOf(#T)", RuntimeTypes.KtorServerHTTP.Cbor)
                }
            }

            writer.withBlock(
                "public val ContentTypeGuard: #T<ContentTypeGuardConfig> = #T(",
                ")",
                RuntimeTypes.KtorServerCore.ApplicationRouteScopedPlugin,
                RuntimeTypes.KtorServerCore.ApplicationCreateRouteScopedPlugin,
            ) {
                write("name = #S,", "ContentTypeGuard")
                write("createConfiguration = ::ContentTypeGuardConfig,")
            }
                .withBlock("{", "}") {
                    write("val allowed: List<#T> = pluginConfig.allow", RuntimeTypes.KtorServerHTTP.ContentType)
                    write("require(allowed.isNotEmpty()) { #S }", "ContentTypeGuard installed with empty allow-list.")
                    write("")
                    withBlock("onCall { call ->", "}") {
                        write("val incoming = call.request.#T()", RuntimeTypes.KtorServerRouting.requestContentType)
                        withBlock("if (incoming == #T.Any || allowed.none { incoming.match(it) }) {", "}", RuntimeTypes.KtorServerHTTP.ContentType) {
                            withBlock("call.#T(", ")", RuntimeTypes.KtorServerRouting.responseRespond) {
                                write("#T.UnsupportedMediaType,", RuntimeTypes.KtorServerHTTP.HttpStatusCode)
                                write("#S", "Allowed Content-Type(s): \${allowed.joinToString()}")
                            }
                            write("return@onCall")
                        }
                    }
                }
        }
    }

    // Emits one stub handler per Smithy operation (`OperationNameHandler.kt`).
    private fun renderPerOperationHandlers() {
        val operationShapes = shapes.filter { it.type == ShapeType.OPERATION }.map { it as OperationShape }
        operationShapes.forEach { shape ->
            val name = shape.id.name
            delegator.useFileWriter("${name}Operation.kt", "${settings.pkg.name}.operations") { writer ->
                writer.addImport("${settings.pkg.name}.model", "${shape.id.name}Request")
                writer.addImport("${settings.pkg.name}.model", "${shape.id.name}Response")

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
