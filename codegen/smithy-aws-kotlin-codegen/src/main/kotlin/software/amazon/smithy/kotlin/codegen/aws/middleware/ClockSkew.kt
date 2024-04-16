/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.aws.middleware

import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.integration.AppendingSectionWriter
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.integration.SectionWriterBinding
import software.amazon.smithy.kotlin.codegen.rendering.ServiceClientGenerator

/**
 * Adds a section writer which applies an interceptor that detects and corrects clock skew
 */
class ClockSkew : KotlinIntegration {
    override val sectionWriters: List<SectionWriterBinding>
        get() = listOf(
            SectionWriterBinding(
                ServiceClientGenerator.Sections.CompanionObject.FinalizeConfig,
                clockSkewSectionWriter,
            ),
        )

    private val clockSkewSectionWriter = AppendingSectionWriter { writer ->
        val interceptorSymbol = RuntimeTypes.AwsProtocolCore.ClockSkewInterceptor
        writer.write("builder.config.interceptors.add(0, #T())", interceptorSymbol)
    }
}
