/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen

import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.build.SmithyBuildPlugin
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.core.KotlinSymbolProvider
import software.amazon.smithy.model.Model

/**
 * Plugin to trigger Kotlin code generation.
 */
class KotlinCodegenPlugin : SmithyBuildPlugin {
    override fun getName(): String = "kotlin-codegen"

    override fun execute(context: PluginContext?) {
        println("executing kotlin codegen")
        CodegenVisitor(context!!).execute()
    }

    companion object {
        /**
         * Creates a Kotlin symbol provider.
         * @param model The model to generate symbols for
         * @param settings Codegen settings
         * @return Returns the created provider
         */
        fun createSymbolProvider(model: Model, settings: KotlinSettings): SymbolProvider =
            KotlinSymbolProvider(model, settings)
    }
}
