/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.signing

import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolMiddleware
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigProperty
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigPropertyType
import software.amazon.smithy.model.Model

class AwsSignerIntegration : KotlinIntegration {
    override fun additionalServiceConfigProps(ctx: CodegenContext): List<ConfigProperty> = listOf(
        ConfigProperty {
            symbol = RuntimeTypes.Auth.Signing.AwsSigningCommon.AwsSigner
            name = "signer"
            documentation = "The implementation of AWS signer to use for signing requests"
            propertyType = ConfigPropertyType.RequiredWithDefault("DefaultAwsSigner")
            additionalImports = listOf(
                RuntimeTypes.Auth.Signing.AwsSigningStandard.DefaultAwsSigner,
            )
        },
    )

    override fun customizeMiddleware(
        ctx: ProtocolGenerator.GenerationContext,
        resolved: List<ProtocolMiddleware>,
    ): List<ProtocolMiddleware> {
        val serviceName = AwsSignatureVersion4.signingServiceName(ctx.service)
        return resolved + AwsSignatureVersion4(serviceName)
    }

    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean =
        AwsSignatureVersion4.isSupportedAuthentication(model, settings.getService(model))
}
