/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.codegen.rendering.smoketests

import aws.smithy.kotlin.codegen.KotlinSettings
import aws.smithy.kotlin.codegen.core.CodegenContext
import aws.smithy.kotlin.codegen.core.DEFAULT_TEST_SOURCE_SET_ROOT
import aws.smithy.kotlin.codegen.core.KotlinDelegator
import aws.smithy.kotlin.codegen.integration.KotlinIntegration
import aws.smithy.kotlin.codegen.model.hasTrait
import aws.smithy.kotlin.codegen.utils.topDownOperations
import software.amazon.smithy.model.Model
import software.amazon.smithy.smoketests.traits.SmokeTestsTrait

/**
 * Renders smoke test runner for a service if any of the operations have the [SmokeTestsTrait].
 */
class SmokeTestsIntegration : KotlinIntegration {
    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean = model.topDownOperations(settings.service).any { it.hasTrait<SmokeTestsTrait>() }

    override fun writeAdditionalFiles(ctx: CodegenContext, delegator: KotlinDelegator) = delegator.useFileWriter(
        "SmokeTests.kt",
        "${ctx.settings.pkg.name}.smoketests",
        DEFAULT_TEST_SOURCE_SET_ROOT,
    ) { writer ->
        SmokeTestsRunnerGenerator(
            writer,
            ctx,
        ).render()
    }
}
