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

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.ErrorTrait

/**
 * Renders Smithy structure shapes
 */
class StructureGenerator(
    val model: Model,
    private val symbolProvider: SymbolProvider,
    private val writer: KotlinWriter,
    private val shape: StructureShape
) {

    fun render() {
        if (!shape.hasTrait(ErrorTrait::class.java)) {
            renderStructure()
        } else {
            renderError()
        }
    }

    private val sortedMembers: List<MemberShape> = shape.allMembers.values.sortedBy { symbolProvider.toMemberName(it) }
    private var byMemberShape: MutableMap<MemberShape, Pair<String, Symbol>> = mutableMapOf()

    init {
        for (member in sortedMembers) {
            val memberName = symbolProvider.toMemberName(member)
            val memberSymbol = symbolProvider.toSymbol(member)
            byMemberShape[member] = Pair(memberName, memberSymbol)
        }
    }

    /**
     * Renders a normal (non-error) Smithy structure to a Kotlin class
     */
    private fun renderStructure() {
        val symbol = symbolProvider.toSymbol(shape)
        // push context to be used throughout generation of the class
        writer.putContext("class.name", symbol.name)

        // FIXME - enum members take special considerations
        // TODO write shape docs
        // constructor
        writer.openBlock("class \$class.name:L private constructor(builder: BuilderImpl) {")
            .call { renderImmutableProperties() }
            .write("")
            .call { renderCompanionObject() }
            .call { renderToString() }
            .call { renderHashCode() }
            .call { renderEquals() }
            .call { renderCopy() }
            .call { renderJavaBuilderInterface() }
            .call { renderDslBuilderInterface() }
            .call { renderBuilderImpl() }
            .closeBlock("}")
            .write("")

        writer.removeContext("class.name")
    }

    private fun renderImmutableProperties() {
        // generate the immutable properties that are set from a builder
        sortedMembers.forEach {
            val (memberName, memberSymbol) = byMemberShape[it]!!
            // TODO write member docs
            writer.write("val \$1L: \$2T = builder.\$1L", memberName, memberSymbol)
        }
    }

    private fun renderCompanionObject() {
        writer.withBlock("companion object {", "}") {
            write("@JvmStatic")
            write("fun builder(): Builder = BuilderImpl()")
            write("")
            write("operator fun invoke(block: DslBuilder.() -> Unit): \$class.name:L = BuilderImpl().apply(block).build()")
            write("")
        }
    }

    // generate a `toString()` implementation
    private fun renderToString() {}

    // generate a `hashCode()` implementation
    private fun renderHashCode() {}

    // generate a `equals()` implementation
    private fun renderEquals() {}

    // generate a `copy()` implementation
    private fun renderCopy() {
        if (sortedMembers.isEmpty()) return

        writer.write("")
        .write("fun copy(")
            .indent()
                .call {
                    for ((index, member) in sortedMembers.withIndex()) {
                        val (memberName, memberSymbol) = byMemberShape[member]!!
                        val terminator = if (index == sortedMembers.size - 1) "" else ","
                        writer.write("\$1L: \$2T = this.\$1L$terminator", memberName, memberSymbol)
                    }
                }
            .dedent()
            .withBlock("): \$class.name:L = BuilderImpl(this).apply {", "}") {
                for (member in sortedMembers) {
                    val (memberName, _) = byMemberShape[member]!!
                    writer.write("this.\$1L = \$1L", memberName)
                }
            }
    }

    private fun renderJavaBuilderInterface() {
        writer.write("")
            .withBlock("interface Builder {", "}") {
                write("fun build(): \$class.name:L")
                for (member in sortedMembers) {
                    val (memberName, memberSymbol) = byMemberShape[member]!!
                    // we want the type names sans nullability (?) for arguments
                    write("fun \$1L(\$1L: \$2L): Builder", memberName, memberSymbol.name)
                }
            }
    }

    private fun renderDslBuilderInterface() {
        writer.write("")
            .withBlock("interface DslBuilder {", "}") {
                val structMembers: MutableList<MemberShape> = mutableListOf()

                for (member in sortedMembers) {
                    val (memberName, memberSymbol) = byMemberShape[member]!!
                    val targetShape = model.getShape(member.target).get()
                    if (targetShape.isStructureShape) {
                        structMembers.add(member)
                    }

                    write("var \$L: \$T", memberName, memberSymbol)
                }

                for (member in structMembers) {
                    val (memberName, memberSymbol) = byMemberShape[member]!!
                    write("")
                    .openBlock("fun \$L(block: \$L.DslBuilder.() -> Unit) {", memberName, memberSymbol.name)
                        .write("this.\$L = \$L.invoke(block)", memberName, memberSymbol.name)
                    .closeBlock("}")
                }
            }
    }

    private fun renderBuilderImpl() {
        writer.write("")
            .withBlock("private class BuilderImpl() : Builder, DslBuilder {", "}") {
                // write only the non-struct shapes, struct shapes invoke the DSL builders of the underlying shape and is handled
                // in the DslBuilder interface itself
                for (member in sortedMembers) {
                    val (memberName, memberSymbol) = byMemberShape[member]!!
                    write("override var \$L: \$D", memberName, memberSymbol)
                }

                write("")

                // generate the constructor that converts from the underlying immutable class to a builder instance
                withBlock("constructor(x: \$class.name:L) : this() {", "}") {
                    for (member in sortedMembers) {
                        val (memberName, _) = byMemberShape[member]!!
                        write("this.\$1L = x.\$1L", memberName)
                    }
                }

                // generate the Java builder overrides
                write("")
                write("override fun build(): \$class.name:L = \$class.name:L(this)")
                for (member in sortedMembers) {
                    val (memberName, memberSymbol) = byMemberShape[member]!!
                    // we want the type names sans nullability (?) for arguments
                    write("override fun \$1L(\$1L: \$2L): Builder = apply { this.\$1L = \$1L }", memberName, memberSymbol.name)
                }
            }
    }

    /**
     * Renders a Smithy error type to a Kotlin exception type
     */
    private fun renderError() {
        // TODO
    }
}
