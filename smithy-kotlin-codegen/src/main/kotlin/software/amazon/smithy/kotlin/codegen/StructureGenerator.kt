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
import software.amazon.smithy.model.traits.EnumTrait
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

        writer.renderDocumentation(shape)
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
            writer.renderMemberDocumentation(model, it)

            // handle enums
            val targetShape = model.getShape(it.target).get()
            if (targetShape.hasTrait(EnumTrait::class.java)) {
                writer.write("val \$1LAsString: \$2L = builder.\$1LAsString", memberName, "String?")
                    .write("val \$1L: \$2T", memberName, memberSymbol)
                    .indent()
                    .write("get() = \$1LAsString?.let { \$2L.fromValue(it) }", memberName, memberSymbol.name)
                    .dedent()
            } else {
                writer.write("val \$1L: \$2T = builder.\$1L", memberName, memberSymbol)
            }
        }
    }

    private fun renderCompanionObject() {
        writer.withBlock("companion object {", "}") {
            write("@JvmStatic")
            write("fun builder(): Builder = BuilderImpl()")
            write("")
            write("fun dslBuilder(): DslBuilder = BuilderImpl()")
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

        // copy has to go through a builder, if we were to generate a "normal"
        // data class copy() with defaults for all properties we would end up in the same
        // situation we have with constructors and positional arguments not playing well
        // with models evolving over time (e.g. new fields in different positions)
        writer.write("")
        .write("fun copy(block: DslBuilder.() -> Unit = {}): \$class.name:L = BuilderImpl(this).apply(block).build()")
            .write("")
    }

    private fun renderJavaBuilderInterface() {
        writer.write("")
            .withBlock("interface Builder {", "}") {
                write("fun build(): \$class.name:L")
                for (member in sortedMembers) {
                    val (memberName, memberSymbol) = byMemberShape[member]!!
                    // we want the type names sans nullability (?) for arguments
                    write("fun \$1L(\$1L: \$2L): Builder", memberName, memberSymbol.name)

                    // handle enums which get an additional override
                    val targetShape = model.getShape(member.target).get()
                    if (targetShape.hasTrait(EnumTrait::class.java)) {
                        // NOTE: we break the fluent builder here on purpose. The BuilderImpl implements both the
                        // Java and Dsl builder interfaces. The overrides that allow a raw string conflict. Rather
                        // than different method names for the override or different builders we chose to break
                        // the fluency for the Java builder since this is an escape hatch not meant to be often used.
                        write("fun \$1L(\$1L: \$2L)", memberName, "String")
                    }
                }
            }
    }

    private fun renderDslBuilderInterface() {
        writer.write("")
            .withBlock("interface DslBuilder {", "}") {
                val structMembers: MutableList<MemberShape> = mutableListOf()
                val enumMembers: MutableList<MemberShape> = mutableListOf()

                for (member in sortedMembers) {
                    val (memberName, memberSymbol) = byMemberShape[member]!!
                    val targetShape = model.getShape(member.target).get()
                    when {
                        targetShape.isStructureShape -> structMembers.add(member)
                        targetShape.hasTrait(EnumTrait::class.java) -> enumMembers.add(member)
                    }

                    write("var \$L: \$T", memberName, memberSymbol)
                }

                // generate overloads for enums
                for (member in enumMembers) {
                    val (memberName, _) = byMemberShape[member]!!
                    write("")
                    write("fun \$1L(\$1L: \$2L)", memberName, "String")
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
                // override DSL properties
                val enumMembers: MutableList<MemberShape> = mutableListOf()
                for (member in sortedMembers) {
                    val (memberName, memberSymbol) = byMemberShape[member]!!

                    val targetShape = model.getShape(member.target).get()
                    if (targetShape.hasTrait(EnumTrait::class.java)) {
                        enumMembers.add(member)
                    } else {
                        write("override var \$L: \$D", memberName, memberSymbol)
                    }
                }

                // generate enum gymnastics
                for (member in enumMembers) {
                    val (memberName, memberSymbol) = byMemberShape[member]!!
                    write("var \$1LAsString: String? = null", memberName)
                    write("override var \$1L: \$2D", memberName, memberSymbol)
                        .indent()
                            .openBlock("set(value) {")
                                .write("\$1LAsString = value.toString()", memberName)
                                .write("field = value")
                            .closeBlock("}")
                        .dedent()
                    write("")
                }

                write("")

                // generate the constructor that converts from the underlying immutable class to a builder instance
                withBlock("constructor(x: \$class.name:L) : this() {", "}") {
                    for (member in sortedMembers) {
                        val (memberName, _) = byMemberShape[member]!!
                        val targetShape = model.getShape(member.target).get()
                        // enums are always set using the backing string field not the typed enum
                        val suffix = if (targetShape.hasTrait(EnumTrait::class.java)) "AsString" else ""
                        write("this.\$1L$suffix = x.\$1L$suffix", memberName)
                    }
                }

                // generate the Java builder overrides
                // NOTE: The enum overloads are the same in both the Java and DslBuilder interfaces, generating
                // the Java builder implementation will satisfy the DslInterface w.r.t enum overloads
                write("")
                write("override fun build(): \$class.name:L = \$class.name:L(this)")
                for (member in sortedMembers) {
                    val (memberName, memberSymbol) = byMemberShape[member]!!
                    val targetShape = model.getShape(member.target).get()
                    // enums are always set using the backing string field not the typed enum
                    if (targetShape.hasTrait(EnumTrait::class.java)) {
                        write("override fun \$1L(\$1L: \$2L): Builder = apply { this.\$1LAsString = \$1L.toString() }", memberName, memberSymbol.name)
                        write("override fun \$1L(\$1L: \$2L) { this.\$1LAsString = \$1L }", memberName, "String")
                    } else {
                        // we want the type names sans nullability (?) for arguments
                        write("override fun \$1L(\$1L: \$2L): Builder = apply { this.\$1L = \$1L }", memberName, memberSymbol.name)
                    }
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
