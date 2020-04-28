/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.amazon.smithy.kotlin.codegen

import software.amazon.smithy.codegen.core.SymbolDependency
import software.amazon.smithy.codegen.core.SymbolDependencyContainer

enum class KotlinDependency (val type: String, val namespace: String): SymbolDependencyContainer {
    BIG("stdlib", "java.math");

    val dependency: SymbolDependency

    init {
        dependency = SymbolDependency.builder()
            .dependencyType(type)
            .build()
    }

    override fun getDependencies(): List<SymbolDependency> {
        return listOf(dependency)
    }

}