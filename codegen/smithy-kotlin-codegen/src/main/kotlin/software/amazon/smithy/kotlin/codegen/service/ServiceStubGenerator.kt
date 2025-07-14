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

internal interface ServiceStubGenerator {
    fun render()
}

internal abstract class AbstractStubGenerator(
    protected val ctx: GenerationContext,
    protected val delegator: KotlinDelegator,
    protected val fileManifest: FileManifest,
) : ServiceStubGenerator {

    protected val serviceShape = ctx.settings.getService(ctx.model)
    protected val operations = TopDownIndex.of(ctx.model)
        .getContainedOperations(serviceShape)
        .sortedBy { it.defaultName() }

    protected val pkgName = ctx.settings.pkg.name

    final override fun render() {
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

    /** Emits the `ServiceFrameworkConfig.kt` file. */
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

    /** Emits ServiceFramework.kt and other engine bootstrap code. */
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

    protected abstract fun renderServerFrameworkImplementation(writer: KotlinWriter)

    /** Emits content-type guards, error handler plugins, … */
    protected abstract fun renderPlugins()

    /** Emits Logback XML + KLogger wiring. */
    protected abstract fun renderLogging()

    /** Auth interfaces & installers (bearer, IAM, …). */
    protected abstract fun renderAuthModule()

    /** Request-level Smithy constraint validators. */
    protected abstract fun renderConstraintValidators()

    /** One handler file per Smithy operation. */
    protected abstract fun renderPerOperationHandlers()

    /** Route table that maps operations → runtime endpoints. */
    protected abstract fun renderRouting()

    /** Writes the top-level `Main.kt` launcher. */
    protected fun renderMainFile() {
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
}
