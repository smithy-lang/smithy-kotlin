/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen

import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.build.SmithyBuildPlugin
import software.amazon.smithy.codegen.core.SymbolProvider
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
         * @param rootNamespace All symbols will be created under this namespace (package) or as a direct child of it.
         * e.g. `com.foo` would create symbols under the `com.foo` package or `com.foo.model` package, etc.
         * @param sdkId name to use to represent client type.  e.g. an sdkId of "foo" would produce a client type "FooClient".
         * @return Returns the created provider
         */
        fun createSymbolProvider(model: Model, rootNamespace: String, sdkId: String): SymbolProvider =
            SymbolVisitor(model, rootNamespace, sdkId)
    }
}
