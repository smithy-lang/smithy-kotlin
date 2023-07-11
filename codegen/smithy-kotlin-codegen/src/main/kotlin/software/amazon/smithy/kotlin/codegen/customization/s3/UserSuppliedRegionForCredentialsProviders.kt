package software.amazon.smithy.kotlin.codegen.customization.s3

import software.amazon.smithy.aws.traits.ServiceTrait
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.integration.SectionWriterBinding
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.kotlin.codegen.rendering.util.AbstractConfigGenerator
import software.amazon.smithy.model.Model

class UserSuppliedRegionForCredentialsProviders: KotlinIntegration {
    override val sectionWriters: List<SectionWriterBinding> = listOf(
        SectionWriterBinding(AbstractConfigGenerator.CustomConfigPropertyType) { writer, default ->
            if(default != null && default.contains("override val credentialsProvider: CredentialsProvider")) {
                writer.withBlock(
                    "override val credentialsProvider: CredentialsProvider = when(builder.credentialsProvider) {",
                    "} ?: DefaultChainCredentialsProvider(httpClient = httpClient, region = region).manage()")
                {
                    withBlock(
                        "is aws.sdk.kotlin.runtime.auth.credentials.ProfileCredentialsProvider -> {",
                        "}"
                    ) {
                        write("if ((builder.credentialsProvider as aws.sdk.kotlin.runtime.auth.credentials.ProfileCredentialsProvider).getRegion() == null) " +
                                "(builder.credentialsProvider as aws.sdk.kotlin.runtime.auth.credentials.ProfileCredentialsProvider).setRegion(region)")
                        write("builder.credentialsProvider")
                    }
                    withBlock(
                        "is aws.sdk.kotlin.runtime.auth.credentials.StsAssumeRoleCredentialsProvider -> {",
                        "}"
                    ) {
                        write("if ((builder.credentialsProvider as aws.sdk.kotlin.runtime.auth.credentials.StsAssumeRoleCredentialsProvider).getRegion() == null) " +
                                "(builder.credentialsProvider as aws.sdk.kotlin.runtime.auth.credentials.StsAssumeRoleCredentialsProvider).setRegion(region)")
                        write("builder.credentialsProvider")
                    }
                    write("else -> builder.credentialsProvider")
                }
            } else {
                writer.write(default)
            }
        },
    )

    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean =
        model.expectShape(settings.service).getTrait<ServiceTrait>()?.sdkId.equals("s3", true)
}