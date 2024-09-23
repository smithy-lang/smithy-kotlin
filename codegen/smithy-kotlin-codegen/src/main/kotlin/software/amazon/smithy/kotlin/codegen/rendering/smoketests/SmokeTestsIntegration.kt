package software.amazon.smithy.kotlin.codegen.rendering.smoketests

import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.utils.topDownOperations
import software.amazon.smithy.model.Model
import software.amazon.smithy.smoketests.traits.SmokeTestsTrait

/**
 * Renders smoke test runner for a service if any of the operations have the [SmokeTestsTrait].
 */
class SmokeTestsIntegration : KotlinIntegration {
    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean =
        model.topDownOperations(settings.service).any { it.hasTrait<SmokeTestsTrait>() }

    override fun writeAdditionalFiles(ctx: CodegenContext, delegator: KotlinDelegator) =
        delegator.useFileWriter(
            "SmokeTests.kt",
            "${ctx.settings.pkg.name}.smoketests",
            "./jvm-src/test/java/",
        ) { writer ->
            SmokeTestsRunnerGenerator(
                writer,
                ctx,
            ).render()
        }
}
