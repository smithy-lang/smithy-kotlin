package software.amazon.smithy.kotlin.codegen.rendering.smoketests

import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.utils.operations
import software.amazon.smithy.model.Model
import software.amazon.smithy.smoketests.traits.SmokeTestsTrait

/**
 * Renders smoke test runner for a service if any of the operations has the smoke test trait.
 */
class SmokeTestsIntegration : KotlinIntegration {
    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean =
        model.operations(settings.service).any { it.hasTrait<SmokeTestsTrait>() } && !smokeTestDenyList.contains(settings.sdkId)

    override fun writeAdditionalFiles(ctx: CodegenContext, delegator: KotlinDelegator) =
        delegator.useFileWriter("SmokeTests.kt", "smoketests", "./jvm-src/main/java/") { writer ->
            SmokeTestsRunnerGenerator(
                writer,
                ctx.symbolProvider.toSymbol(ctx.model.expectShape(ctx.settings.service)),
                ctx.model.operations(ctx.settings.service).filter { it.hasTrait<SmokeTestsTrait>() },
                ctx.model,
                ctx.symbolProvider,
                ctx.settings.sdkId,
            ).render()
        }
}

/**
 * SDK ID's of services that model smoke tests incorrectly
 */
val smokeTestDenyList = setOf(
    "Application Auto Scaling",
    "SWF",
    "WAFV2",
)
