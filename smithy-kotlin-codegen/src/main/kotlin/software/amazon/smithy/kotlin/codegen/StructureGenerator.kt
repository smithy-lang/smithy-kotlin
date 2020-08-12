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

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.ShapeType
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.ErrorTrait
import software.amazon.smithy.model.traits.RetryableTrait
import software.amazon.smithy.model.traits.SensitiveTrait

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
        startGenericStructureBlock("class \$class.name:L private constructor(builder: BuilderImpl) {")
        writer.closeBlock("}").write("")

        writer.removeContext("class.name")
    }

    private fun startGenericStructureBlock(start: String) {
        val symbol = symbolProvider.toSymbol(shape)
        // push context to be used throughout generation of the class
        writer.putContext("class.name", symbol.name)

        writer.renderDocumentation(shape)
        // constructor
        writer.openBlock(start)
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
    }

    private fun renderImmutableProperties() {
        // generate the immutable properties that are set from a builder
        sortedMembers.forEach {
            val (memberName, memberSymbol) = byMemberShape[it]!!
            writer.renderMemberDocumentation(model, it)
            if (shape.hasTrait(ErrorTrait::class.java) && "message" == memberName) {
                // TODO: Have to handle the case where "cause" is a property in the Smithy model
                val targetShape = model.getShape(it.target).get()
                if (!targetShape.isStringShape) {
                    throw CodegenException("Message is a reserved name for exception types and cannot be used for any other property")
                }
                // Override Throwable's message property
                writer.write("override val \$1L: \$2L = builder.\$1L!!", memberName, memberSymbol)
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
    private fun renderToString() {
        writer.write("")
        writer.withBlock("override fun toString() = buildString {", "}") {
            write("append(\"\$class.name:L(\")")

            if (sortedMembers.isEmpty()) {
                write("append(\")\")")
            } else {
                sortedMembers.forEachIndexed { index, memberShape ->
                    val memberName = memberShape.defaultName()
                    val separator = if (index < sortedMembers.size - 1) "," else ")"

                    val targetShape = model.expectShape(memberShape.target)
                    if (targetShape.hasTrait(SensitiveTrait::class.java)) {
                        write("append(\"\$1L=*** Sensitive Data Redacted ***$separator\")", memberName)
                    } else {
                        write("append(\"\$1L=$$\$1L$separator\")", memberName)
                    }
                }
            }
        }
    }

    // generate a `hashCode()` implementation
    private fun renderHashCode() {
        writer.write("")
        writer.withBlock("override fun hashCode(): Int {", "}") {
            if (sortedMembers.isEmpty()) {
                write("var result = javaClass.hashCode()")
            } else {
                write(
                    "var result = \$1L\$2L",
                    sortedMembers[0].defaultName(),
                    selectHashFunctionForShape(sortedMembers[0])
                )

                if (sortedMembers.size > 1) {
                    for ((index, memberShape) in sortedMembers.withIndex()) {
                        if (index == 0) continue

                        write(
                            "result = 31 * result + (\$1L\$2L)",
                            memberShape.defaultName(), selectHashFunctionForShape(memberShape)
                        )
                    }
                }
            }
            write("return result")
        }
    }

    // Return the appropriate hashCode fragment based on ShapeID of member target.
    private fun selectHashFunctionForShape(member: MemberShape): String {
        val targetShape = model.expectShape(member.target)
        // also available already in the byMember map
        val targetSymbol = symbolProvider.toSymbol(targetShape)

        return when (targetShape.type) {
            ShapeType.INTEGER ->
                when (targetSymbol.isBoxed()) {
                    true -> " ?: 0"
                    else -> ""
                }
            ShapeType.BYTE ->
                when (targetSymbol.isBoxed()) {
                    true -> "?.toInt() ?: 0"
                    else -> ".toInt()"
                }
            else ->
                when (targetSymbol.isBoxed()) {
                    true -> "?.hashCode() ?: 0"
                    else -> ".hashCode()"
                }
        }
    }

    // generate a `equals()` implementation
    private fun renderEquals() {
        writer.write("")
        writer.withBlock("override fun equals(other: Any?): Boolean {", "}") {
            write("if (this === other) return true")
            write("if (javaClass != other?.javaClass) return false")
            write("")
            write("other as \$class.name:L")
            write("")

            for (memberShape in sortedMembers) {
                write("if (\$1L != other.\$1L) return false", memberShape.defaultName())
            }

            write("")
            write("return true")
        }
    }

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
                    when {
                        targetShape.isStructureShape -> structMembers.add(member)
                    }

                    write("var \$L: \$T", memberName, memberSymbol)
                }

                write("")
                write("fun build(): \$class.name:L")
                for (member in structMembers) {
                    val (memberName, memberSymbol) = byMemberShape[member]!!
                    openBlock("fun \$L(block: \$L.DslBuilder.() -> Unit) {", memberName, memberSymbol.name)
                        .write("this.\$L = \$L.invoke(block)", memberName, memberSymbol.name)
                    .closeBlock("}")
                }
            }
    }

    private fun renderBuilderImpl() {
        writer.write("")
            .withBlock("private class BuilderImpl() : Builder, DslBuilder {", "}") {
                // override DSL properties
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
                // NOTE: The enum overloads are the same in both the Java and DslBuilder interfaces, generating
                // the Java builder implementation will satisfy the DslInterface w.r.t enum overloads
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
        var errorTrait: ErrorTrait = shape.expectTrait(ErrorTrait::class.java)

        var isRetryable = shape.getTrait(RetryableTrait::class.java).isPresent

        startGenericStructureBlock("class \$class.name:L private constructor(builder: BuilderImpl) : ServiceException() {")
        writer.withBlock("", "}") {
            write("")
            if (isRetryable) {
                call { renderRetryable() }
            }
            call { renderErrorType(errorTrait) }
        }
        writer.write("")
        writer.removeContext("class.name")
    }

    private fun renderRetryable() {
        writer.write("")
        writer.write("override val isRetryable = true")
    }

    private fun renderErrorType(errorTrait: ErrorTrait) {
        writer.write("")
        when {
            errorTrait.isClientError -> {
                writer.write("override val errorType = ErrorType.Client")
            }
            errorTrait.isServerError -> {
                writer.write("override val errorType = ErrorType.Server")
            }
            else -> {
                throw CodegenException("Errors must be either of client or server type")
            }
        }
    }
}
