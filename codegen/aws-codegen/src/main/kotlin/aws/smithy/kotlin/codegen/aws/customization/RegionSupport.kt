/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.aws.customization

import aws.smithy.kotlin.codegen.KotlinSettings
import aws.smithy.kotlin.codegen.core.CodegenContext
import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.core.RuntimeTypes
import aws.smithy.kotlin.codegen.core.getContextValue
import aws.smithy.kotlin.codegen.integration.AppendingSectionWriter
import aws.smithy.kotlin.codegen.integration.KotlinIntegration
import aws.smithy.kotlin.codegen.integration.SectionWriterBinding
import aws.smithy.kotlin.codegen.lang.KotlinTypes
import aws.smithy.kotlin.codegen.model.defaultName
import aws.smithy.kotlin.codegen.model.expectShape
import aws.smithy.kotlin.codegen.model.getEndpointRules
import aws.smithy.kotlin.codegen.model.hasTrait
import aws.smithy.kotlin.codegen.model.knowledge.AwsSignatureVersion4
import aws.smithy.kotlin.codegen.model.nullable
import aws.smithy.kotlin.codegen.rendering.endpoints.EndpointCustomization
import aws.smithy.kotlin.codegen.rendering.protocol.HttpProtocolClientGenerator
import aws.smithy.kotlin.codegen.rendering.protocol.HttpProtocolUnitTestRequestGenerator
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import aws.smithy.kotlin.codegen.rendering.protocol.putIfAbsent
import aws.smithy.kotlin.codegen.rendering.util.ConfigProperty
import software.amazon.smithy.aws.traits.ServiceTrait
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.rulesengine.language.EndpointRuleSet
import software.amazon.smithy.rulesengine.language.syntax.parameters.Parameter

/**
 * Registers support for the concept of region on the service client config, endpoint builtins, etc.
 *
 * Region is enabled IFF sigv4(a) is enabled or an AWS SDK service is targeted
 */
class RegionSupport : KotlinIntegration {
    companion object {
        const val BUILTIN_NAME = "AWS::Region"

        val RegionProp: ConfigProperty = ConfigProperty {
            name = "region"
            symbol = KotlinTypes.String.toBuilder().nullable().build()
            documentation = """
                The AWS region to sign with and make requests to. When specified, this static region configuration
                takes precedence over other region resolution methods. 
                
                The region resolution order is:
                1. Static region (if specified)
                2. Custom region provider (if configured)
                3. Default region provider chain
            """.trimIndent()
        }

        val RegionProviderProp: ConfigProperty = ConfigProperty {
            name = "regionProvider"
            symbol = RuntimeTypes.SmithyClient.Region.RegionProvider
            documentation = """
                An optional region provider that determines the AWS region for client operations. When specified, this provider
                takes precedence over the default region provider chain, unless a static region is explicitly configured.
              
                The region resolution order is:
                1. Static region (if specified)
                2. Custom region provider (if configured)
                3. Default region provider chain
            """.trimIndent()
        }
    }

    // Allow other integrations to customize the service config props, later integrations take precedence.
    // This is used by AWS SDK codegen to customize the base class and documentation for this property
    override val order: Byte = -50

    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean {
        val service = model.expectShape<ServiceShape>(settings.service)
        val supportsSigv4 = AwsSignatureVersion4.isSupportedAuthentication(model, service)
        val hasRegionBuiltin = service.getEndpointRules()?.parameters?.find { it.isBuiltIn && it.builtIn.get() == BUILTIN_NAME } != null
        val isAwsSdk = service.hasTrait<ServiceTrait>()
        return supportsSigv4 || hasRegionBuiltin || isAwsSdk
    }

    override fun additionalServiceConfigProps(ctx: CodegenContext): List<ConfigProperty> = listOf(RegionProp, RegionProviderProp)

    override fun customizeEndpointResolution(ctx: ProtocolGenerator.GenerationContext): EndpointCustomization =
        object : EndpointCustomization {
            override fun renderBindEndpointBuiltins(
                ctx: ProtocolGenerator.GenerationContext,
                rules: EndpointRuleSet,
                writer: KotlinWriter,
            ) {
                val builtins = rules.parameters?.toList()?.filter(Parameter::isBuiltIn) ?: return
                builtins.forEach {
                    when (it.builtIn.get()) {
                        BUILTIN_NAME -> writer.write("#L = config.#L", it.defaultName(), RegionProp.propertyName)
                    }
                }
            }
        }
    override val sectionWriters: List<SectionWriterBinding>
        get() = listOf(
            SectionWriterBinding(HttpProtocolUnitTestRequestGenerator.ConfigureServiceClient, renderHttpProtocolRequestTestConfigureServiceClient),
            SectionWriterBinding(HttpProtocolClientGenerator.MergeServiceDefaults, renderRegionOperationContextDefault),
        )

    // sets a default region for protocol tests
    private val renderHttpProtocolRequestTestConfigureServiceClient = AppendingSectionWriter { writer ->
        // specify a default region
        writer.write("region = #S", "us-east-1")
    }

    // sets (initial) region/signing region in the execution context
    private val renderRegionOperationContextDefault = AppendingSectionWriter { writer ->
        val ctx = writer.getContextValue(HttpProtocolClientGenerator.MergeServiceDefaults.GenerationContext)
        val isAwsSdk = ctx.service.hasTrait<ServiceTrait>()

        if (isAwsSdk) {
            writer.putIfAbsent(
                RuntimeTypes.AwsProtocolCore.AwsAttributes,
                "Region",
                "config.region",
                nullable = true,
            )
        }

        writer.putIfAbsent(
            RuntimeTypes.Auth.Signing.AwsSigningCommon.AwsSigningAttributes,
            "SigningRegion",
            "config.region",
            nullable = true,
        )
    }
}
