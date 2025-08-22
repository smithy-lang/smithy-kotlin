package software.amazon.smithy.kotlin.codegen.service.ktor

import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.kotlin.codegen.core.GenerationContext
import software.amazon.smithy.kotlin.codegen.core.InlineCodeWriterFormatter
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.service.AbstractStubGenerator
import software.amazon.smithy.kotlin.codegen.service.constraints.ConstraintGenerator
import software.amazon.smithy.kotlin.codegen.service.constraints.ConstraintUtilsGenerator
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

/**
 * Stub generator for Ktor-based services.
 *
 * Implements [AbstractStubGenerator] for the Ktor runtime, generating:
 * - Framework implementation
 * - Utilities
 * - Authentication modules
 * - Constraint validators
 * - Routing tables
 * - Plugins
 * - Operation handlers
 */
internal class KtorStubGenerator(
    ctx: GenerationContext,
    delegator: KotlinDelegator,
    fileManifest: FileManifest,
) : AbstractStubGenerator(ctx, delegator, fileManifest) {

    /** Generate the Ktor server framework implementation. */
    override fun renderServerFrameworkImplementation(writer: KotlinWriter) = writeServerFrameworkImplementation(writer)

    /** Generate utility classes and helpers. */
    override fun renderUtils() = writeUtils()

    /** Generate authentication modules (AWS auth + bearer/no-auth). */
    override fun renderAuthModule() {
        writeAWSAuthentication()
        writeAuthentication()
    }

    /** Generate request constraint validators for all operations. */
    override fun renderConstraintValidators() {
        ConstraintUtilsGenerator(ctx, delegator).render()
        operations.forEach { operation -> ConstraintGenerator(ctx, operation, delegator).render() }
    }

    /** Generate routing file mapping Smithy operations to Ktor routes. */
    override fun renderRouting() = writeRouting()

    /** Generate plugin configurations (e.g., error handlers, guards). */
    override fun renderPlugins() = writePlugins()

    /** Generate stub handler files for each Smithy operation. */
    override fun renderPerOperationHandlers() = writePerOperationHandlers()
}
