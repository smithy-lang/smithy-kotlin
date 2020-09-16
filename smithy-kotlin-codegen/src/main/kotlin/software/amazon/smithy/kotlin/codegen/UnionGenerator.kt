/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
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
        writer.renderDocumentation(shape)
        writer.openBlock("sealed class \$L {", symbol.name)
        shape.allMembers.values.sortedBy { it.memberName }.forEach {
            writer.renderMemberDocumentation(model, it)
            val memberName = symbolProvider.toMemberName(it)
            writer.write("data class \$L(val \$L: \$L) : \$L()", memberName.capitalize(), memberName, symbolProvider.toSymbol(it).name, symbol.name)
        }
        writer.closeBlock("}").write("")
    }
}
