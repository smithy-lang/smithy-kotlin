package software.amazon.smithy.kotlin.codegen.rendering.checksums

import software.amazon.smithy.aws.traits.HttpChecksumTrait
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigProperty
import software.amazon.smithy.kotlin.codegen.utils.topDownOperations
import software.amazon.smithy.model.Model

/**
 * todo
 */
class HttpChecksumIntegration : KotlinIntegration {
    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean =
        model.topDownOperations(settings.service).any { it.hasTrait(HttpChecksumTrait::class.java) }

    override fun additionalServiceConfigProps(ctx: CodegenContext): List<ConfigProperty> =
        listOf(
            ConfigProperty {
                name = "requestChecksumCalculation"
                symbol = RuntimeTypes.SmithyClient.Config.ChecksumConfigOption
                baseClass = RuntimeTypes.SmithyClient.Config.HttpChecksumClientConfig
                useNestedBuilderBaseClass()
                documentation = "" // todo
            },
            ConfigProperty {
                name = "responseChecksumValidation"
                symbol = RuntimeTypes.SmithyClient.Config.ChecksumConfigOption
                baseClass = RuntimeTypes.SmithyClient.Config.HttpChecksumClientConfig
                useNestedBuilderBaseClass()
                documentation = "" // todo
            },
        )
}
