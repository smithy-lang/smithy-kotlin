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

import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.UnionShape

/**
 * Renders Smithy union shapes
 */
class UnionGenerator(
    val model: Model,
    private val symbolProvider: SymbolProvider,
    private val writer: KotlinWriter,
    private val shape: UnionShape
) {

    fun render() {
        renderUnion()
    }

    /**
     * Renders a Smithy union to a Kotlin sealed class
     */
    private fun renderUnion() {
        val symbol = symbolProvider.toSymbol(shape)
        writer.openBlock("sealed class \$L {", symbol.name)
        shape.allMembers.values.forEach {
            val memberName = symbolProvider.toMemberName(it)
            writer.write("data class \$L(val \$L: \$L) : \$L()", memberName.capitalize(), memberName, symbolProvider.toSymbol(it).name, symbol.name)
        }
        writer.closeBlock("}").write("")
    }
}
