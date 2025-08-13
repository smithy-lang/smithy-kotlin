package software.amazon.smithy.kotlin.codegen.service

import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.kotlin.codegen.core.GenerationContext
import software.amazon.smithy.kotlin.codegen.core.InlineCodeWriterFormatter
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.service.contraints.ConstraintGenerator
import software.amazon.smithy.kotlin.codegen.service.contraints.ConstraintUtilsGenerator
import software.amazon.smithy.kotlin.codegen.service.ktor.writeAWSAuthentication
import software.amazon.smithy.kotlin.codegen.service.ktor.writeAuthentication
import software.amazon.smithy.kotlin.codegen.service.ktor.writePerOperationHandlers
import software.amazon.smithy.kotlin.codegen.service.ktor.writePlugins
import software.amazon.smithy.kotlin.codegen.service.ktor.writeRouting
import software.amazon.smithy.kotlin.codegen.service.ktor.writeServerFrameworkImplementation
import software.amazon.smithy.kotlin.codegen.service.ktor.writeUtils
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

    override fun renderServerFrameworkImplementation(writer: KotlinWriter) = writeServerFrameworkImplementation(writer)

    override fun renderUtils() = writeUtils()

    // Generates `Authentication.kt` with Authenticator interface + configureSecurity().
    override fun renderAuthModule() {
        writeAWSAuthentication()
        writeAuthentication()
    }

    // For every operation request structure, create a `validate()` function file.
    override fun renderConstraintValidators() {
        ConstraintUtilsGenerator(ctx, delegator).render()
        operations.forEach { operation -> ConstraintGenerator(ctx, operation, delegator).render() }
    }

    // Writes `Routing.kt` that maps Smithy operations → Ktor routes.
    override fun renderRouting() = writeRouting()

    override fun renderPlugins() = writePlugins()

    // Emits one stub handler per Smithy operation (`OperationNameHandler.kt`).
    override fun renderPerOperationHandlers() = writePerOperationHandlers()
}
