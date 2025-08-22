package software.amazon.smithy.kotlin.codegen.service

import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.kotlin.codegen.core.GenerationContext
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.defaultName
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.core.withInlineBlock
import software.amazon.smithy.model.knowledge.TopDownIndex

/**
 * Interface representing a generator that produces service stubs (boilerplate code) for a Smithy service.
 */
internal interface ServiceStubGenerator {
    /**
     * Render the stub code into the target files.
     */
    fun render()
}

/**
 * Abstract base class for generating service stubs.
 *
 * Provides a framework for generating common service artifacts such as:
 * - Configuration (`ServiceFrameworkConfig.kt`)
 * - Framework bootstrap (`ServiceFramework.kt`)
 * - Plugins, utils, authentication, validators
 * - Operation handlers and routing
 * - Main launcher (`Main.kt`)
 *
 * Concrete subclasses must implement abstract methods for framework-specific
 * code (e.g., Ktor).
 */
internal abstract class AbstractStubGenerator(
    val ctx: GenerationContext,
    val delegator: KotlinDelegator,
    val fileManifest: FileManifest,
) : ServiceStubGenerator {

    val serviceShape = ctx.settings.getService(ctx.model)
    val operations = TopDownIndex.of(ctx.model)
        .getContainedOperations(serviceShape)
        .sortedBy { it.defaultName() }

    val pkgName = ctx.settings.pkg.name

    /**
     * Render all service stub files by invoking the component renderers.
     * This acts as the main entrypoint for code generation.
     */
    final override fun render() {
        renderServiceFrameworkConfig()
        renderServiceFramework()
        renderPlugins()
        renderUtils()
        renderAuthModule()
        renderConstraintValidators()
        renderPerOperationHandlers()
        renderRouting()
        renderMainFile()
    }

    /**
     * Generate the service configuration file (`ServiceFrameworkConfig.kt`).
     *
     * Defines enums for:
     * - `LogLevel`: Logging verbosity levels
     * - `ServiceEngine`: Available server engines (Netty, CIO, Jetty)
     *
     * Provides a singleton `ServiceFrameworkConfig` object that stores runtime
     * settings such as port, engine, region, timeouts, and log level.
     */
    protected fun renderServiceFrameworkConfig() {
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
                write("NETTY_ENGINE(#S),", "netty")
                write("CIO_ENGINE(#S),", "cio")
                write("JETTY_JAKARTA_ENGINE(#S),", "jetty-jakarta")
                write(";")
                write("")
                write("override fun toString(): String = value")
                write("")
                withBlock("companion object {", "}") {
                    withBlock("fun fromValue(value: String): #T {", "}", ServiceTypes(pkgName).serviceEngine) {
                        write(
                            "return #T.entries.firstOrNull { it.value.equals(value.lowercase(), ignoreCase = true) } ?: throw IllegalArgumentException(#S)",
                            ServiceTypes(pkgName).serviceEngine,
                            "\$value is not a validContentType value, expected one of \${ServiceEngine.entries}",
                        )
                    }
                }
                write("")
                withBlock("fun toEngineFactory(): #T<*, *> {", "}", RuntimeTypes.KtorServerCore.ApplicationEngineFactory) {
                    withBlock("return when(this) {", "}") {
                        write("NETTY_ENGINE -> #T as #T<*, *>", RuntimeTypes.KtorServerNetty.Netty, RuntimeTypes.KtorServerCore.ApplicationEngineFactory)
                        write("CIO_ENGINE -> #T as #T<*, *>", RuntimeTypes.KtorServerCio.CIO, RuntimeTypes.KtorServerCore.ApplicationEngineFactory)
                        write("JETTY_JAKARTA_ENGINE -> #T as #T<*, *>", RuntimeTypes.KtorServerJettyJakarta.Jetty, RuntimeTypes.KtorServerCore.ApplicationEngineFactory)
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
                    write("val region: String,")
                    write("val requestBodyLimit: Long,")
                    write("val requestReadTimeoutSeconds: Int,")
                    write("val responseWriteTimeoutSeconds: Int,")
                    write("val closeGracePeriodMillis: Long,")
                    write("val closeTimeoutMillis: Long,")
                    write("val logLevel: #T,", ServiceTypes(pkgName).logLevel)
                }
                write("")
                write("val port: Int get() = backing?.port ?: notInitialised(#S)", "port")
                write("val engine: #T get() = backing?.engine ?: notInitialised(#S)", ServiceTypes(pkgName).serviceEngine, "engine")
                write("val region: String get() = backing?.region ?: notInitialised(#S)", "region")
                write("val requestBodyLimit: Long get() = backing?.requestBodyLimit ?: notInitialised(#S)", "requestBodyLimit")
                write("val requestReadTimeoutSeconds: Int get() = backing?.requestReadTimeoutSeconds ?: notInitialised(#S)", "requestReadTimeoutSeconds")
                write("val responseWriteTimeoutSeconds: Int get() = backing?.responseWriteTimeoutSeconds ?: notInitialised(#S)", "responseWriteTimeoutSeconds")
                write("val closeGracePeriodMillis: Long get() = backing?.closeGracePeriodMillis ?: notInitialised(#S)", "closeGracePeriodMillis")
                write("val closeTimeoutMillis: Long get() = backing?.closeTimeoutMillis ?: notInitialised(#S)", "closeTimeoutMillis")
                write("val logLevel: #T get() = backing?.logLevel ?: notInitialised(#S)", ServiceTypes(pkgName).logLevel, "logLevel")
                write("")
                withInlineBlock("fun init(", ")") {
                    write("port: Int,")
                    write("engine: #T,", ServiceTypes(pkgName).serviceEngine)
                    write("region: String,")
                    write("requestBodyLimit: Long,")
                    write("requestReadTimeoutSeconds: Int,")
                    write("responseWriteTimeoutSeconds: Int,")
                    write("closeGracePeriodMillis: Long,")
                    write("closeTimeoutMillis: Long,")
                    write("logLevel: #T,", ServiceTypes(pkgName).logLevel)
                }
                withBlock("{", "}") {
                    write("check(backing == null) { #S }", "ServiceFrameworkConfig has already been initialised")
                    write("backing = Data(port, engine, region, requestBodyLimit, requestReadTimeoutSeconds, responseWriteTimeoutSeconds, closeGracePeriodMillis, closeTimeoutMillis, logLevel)")
                }
                write("")
                withBlock("private fun notInitialised(prop: String): Nothing {", "}") {
                    write("error(#S)", "ServiceFrameworkConfig.\$prop accessed before init()")
                }
            }
        }
    }

    /**
     * Generate the service framework interface and bootstrap (`ServiceFramework.kt`).
     *
     * Declares a common `ServiceFramework` interface with lifecycle methods and
     * delegates framework-specific implementation details to subclasses.
     */
    protected fun renderServiceFramework() {
        delegator.useFileWriter("ServiceFramework.kt", "${ctx.settings.pkg.name}.framework") { writer ->

            writer.withBlock("internal interface ServiceFramework: #T {", "}", RuntimeTypes.Core.IO.Closeable) {
                write("// start the service and begin accepting connections")
                write("public fun start()")
            }
                .write("")

            renderServerFrameworkImplementation(writer)
        }
    }

    /** Render the specific server framework implementation (e.g., Ktor). */
    protected abstract fun renderServerFrameworkImplementation(writer: KotlinWriter)

    /** Generate service plugins such as content-type guards, error handlers, etc. */
    protected abstract fun renderPlugins()

    /** Generate supporting utility classes and functions. */
    protected abstract fun renderUtils()

    /** Generate authentication module interfaces and installers (e.g., bearer auth, SigV4, SigV4A). */
    protected abstract fun renderAuthModule()

    /** Generate request-level constraint validators for Smithy model constraints. */
    protected abstract fun renderConstraintValidators()

    /** Generate a request handler for each Smithy operation. */
    protected abstract fun renderPerOperationHandlers()

    /** Generate the route table that maps Smithy operations to runtime endpoints. */
    protected abstract fun renderRouting()

    /**
     * Generate the top-level `Main.kt` launcher file.
     *
     * This file provides the `main()` entrypoint:
     * - Parses command-line arguments
     * - Applies defaults for configuration values
     * - Initializes the `ServiceFrameworkConfig`
     * - Starts the appropriate service framework
     */
    protected fun renderMainFile() {
        val portName = "port"
        val engineFactoryName = "engineFactory"
        val regionName = "region"
        val requestBodyLimitName = "requestBodyLimit"
        val requestReadTimeoutSecondsName = "requestReadTimeoutSeconds"
        val responseWriteTimeoutSecondsName = "responseWriteTimeoutSeconds"
        val closeGracePeriodMillisName = "closeGracePeriodMillis"
        val closeTimeoutMillisName = "closeTimeoutMillis"
        val logLevelName = "logLevel"
        delegator.useFileWriter("Main.kt", ctx.settings.pkg.name) { writer ->

            writer.withBlock("public fun main(args: Array<String>): Unit {", "}") {
                write("val argMap: Map<String, String> = args.asList().chunked(2).associate { (k, v) -> k.removePrefix(#S) to v }", "--")
                write("")
                write("val defaultPort = 8080")
                write("val defaultEngine = #T.NETTY_ENGINE.value", ServiceTypes(pkgName).serviceEngine)
                write("val defaultRegion = #S", "us-east-1")
                write("val defaultRequestBodyLimit = 10L * 1024 * 1024")
                write("val defaultRequestReadTimeoutSeconds = 30")
                write("val defaultResponseWriteTimeoutSeconds = 30")
                write("val defaultCloseGracePeriodMillis = 1_000L")
                write("val defaultCloseTimeoutMillis = 5_000L")
                write("val defaultLogLevel = #T.INFO.value", ServiceTypes(pkgName).logLevel)
                write("")
                withBlock("#T.init(", ")", ServiceTypes(pkgName).serviceFrameworkConfig) {
                    write("port = argMap[#S]?.toInt() ?: defaultPort, ", portName)
                    write("engine = #T.fromValue(argMap[#S] ?: defaultEngine), ", ServiceTypes(pkgName).serviceEngine, engineFactoryName)
                    write("region = argMap[#S]?.toString() ?: defaultRegion, ", regionName)
                    write("requestBodyLimit = argMap[#S]?.toLong() ?: defaultRequestBodyLimit, ", requestBodyLimitName)
                    write("requestReadTimeoutSeconds = argMap[#S]?.toInt() ?: defaultRequestReadTimeoutSeconds, ", requestReadTimeoutSecondsName)
                    write("responseWriteTimeoutSeconds = argMap[#S]?.toInt() ?: defaultResponseWriteTimeoutSeconds, ", responseWriteTimeoutSecondsName)
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
}
