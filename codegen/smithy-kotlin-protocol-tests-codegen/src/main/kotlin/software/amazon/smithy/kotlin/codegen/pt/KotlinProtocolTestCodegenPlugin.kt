/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.pt

import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.build.SmithyBuildPlugin
import software.amazon.smithy.kotlin.codegen.CodegenVisitor
import software.amazon.smithy.kotlin.codegen.core.GradleConfiguration
import software.amazon.smithy.kotlin.codegen.core.KOTLIN_COMPILER_VERSION
import software.amazon.smithy.kotlin.codegen.core.KotlinDependency
import software.amazon.smithy.kotlin.codegen.rendering.protocol.TestMemberDelta
import software.amazon.smithy.kotlin.codegen.rendering.protocol.pt.ProtocolTestGenerator
import software.amazon.smithy.kotlin.codegen.rendering.writeGradleBuild

// We redefine the kotlin-test and smithy-tests dependencies since for this use case
// we need them in the implementation scope instead of just in the test scope.
val KOTLIN_TEST_RT = KotlinDependency(
    GradleConfiguration.Implementation,
    "kotlin.test",
    "org.jetbrains.kotlin",
    "kotlin-test",
    KOTLIN_COMPILER_VERSION,
)
val SMITHY_TEST_RT = KotlinDependency(
    GradleConfiguration.Implementation,
    KotlinDependency.SMITHY_TEST.namespace,
    KotlinDependency.SMITHY_TEST.group,
    KotlinDependency.SMITHY_TEST.artifact,
    KotlinDependency.SMITHY_TEST.version,
)

/**
 * Plugin to trigger Kotlin protocol tests code generation. This plugin also generates the client and the
 * request/response shapes.
 */
public class KotlinProtocolTestCodegenPlugin : SmithyBuildPlugin {

    override fun getName(): String = "kotlin-protocol-tests-codegen"

    override fun execute(context: PluginContext?) {
        // Run the regular codegen
        var codegen = CodegenVisitor(context ?: error("context was null"))
        codegen.generateShapes()

        // Generate the protocol tests
        val ctx = codegen.generationContext()
        val requestTestBuilder = ProtocolTestRequestGenerator.Builder()
        val responseTestBuilder = ProtocolTestResponseGenerator.Builder()
        val errorTestBuilder = ProtocolTestErrorGenerator.Builder()
        val ignoredTests = TestMemberDelta(
            setOf(),
        )
        ProtocolTestGenerator(
            ctx,
            requestTestBuilder,
            responseTestBuilder,
            errorTestBuilder,
            ignoredTests,
        ).generateProtocolTests()

        val writers = ctx.delegator
        writers.finalize()
        val settings = ctx.settings

        if (settings.build.generateDefaultBuildFiles) {
            val dependencies = writers.dependencies
                .mapNotNull { it.properties["dependency"] as? KotlinDependency }
                .distinct()
            val newDependencies = ArrayList<KotlinDependency>()
            newDependencies.addAll(dependencies)
            newDependencies.add(KOTLIN_TEST_RT)
            newDependencies.add(SMITHY_TEST_RT)
            writeGradleBuild(settings, writers.fileManifest, newDependencies, enableApplication = true)
        }
        writers.flushWriters()
    }
}
