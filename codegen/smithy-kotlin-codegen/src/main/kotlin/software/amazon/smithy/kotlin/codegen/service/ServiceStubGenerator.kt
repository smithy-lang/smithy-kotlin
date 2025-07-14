package software.amazon.smithy.kotlin.codegen.service

import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.kotlin.codegen.core.GenerationContext
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.core.defaultName
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

    // ───────────────────────── Template method ──────────────────────────

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
    protected abstract fun renderServiceFrameworkConfig()

    /** Emits ServiceFramework.kt and other engine bootstrap code. */
    protected abstract fun renderServiceFramework()

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
    protected abstract fun renderMainFile()
}
