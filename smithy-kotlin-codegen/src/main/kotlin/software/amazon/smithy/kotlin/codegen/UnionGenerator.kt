/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen

import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.StreamingTrait
import software.amazon.smithy.utils.CodeWriter

/**
 * Renders Smithy union shapes
 */
class UnionGenerator(
    val model: Model,
    private val symbolProvider: SymbolProvider,
    private val writer: KotlinWriter,
    private val shape: UnionShape
) {

    /**
     * Renders a Smithy union to a Kotlin sealed class
     */
    fun render() {
        check(!shape.allMembers.values.any { memberShape -> memberShape.memberName.equals("SdkUnknown", true) })
            { "generating SdkUnknown would cause duplicate variant for union shape: $shape" }
        val symbol = symbolProvider.toSymbol(shape)
        writer.renderDocumentation(shape)
        writer.openBlock("sealed class \$L {", symbol.name)
        shape.allMembers.values.sortedBy { it.memberName }.forEach {
            writer.renderMemberDocumentation(model, it)
            val memberName = symbolProvider.toMemberName(it)
            val targetType = model.expectShape(it.target).type
            writer.writeInline("data class \$L(val value: \$L) : \$L()", memberName.capitalize(), symbolProvider.toSymbol(it).name, symbol.name)
            when (targetType) {
                ShapeType.BLOB -> {
                    writer.withBlock(" {", "}") {
                        renderHashCode(model, listOf(it), symbolProvider, this)
                        renderEquals(model, listOf(it), memberName.capitalize(), this)
                    }
                }
                else -> writer.write("")
            }
        }
        // generate the unknown which will always be last
        writer.withBlock("data class SdkUnknown(val value: kotlin.String) : ${symbol.name}() {", "}") {
            renderToStringOverride()
        }
        writer.closeBlock("}").write("")
    }

    // generate a `hashCode()` implementation
    private fun renderHashCode(model: Model, sortedMembers: List<MemberShape>, symbolProvider: SymbolProvider, writer: CodeWriter) {
        writer.write("")
        writer.withBlock("override fun hashCode(): Int {", "}") {
            write("return value\$L", selectHashFunctionForShape(model, sortedMembers[0], symbolProvider))
        }
    }

    // Return the appropriate hashCode fragment based on ShapeID of member target.
    private fun selectHashFunctionForShape(model: Model, member: MemberShape, symbolProvider: SymbolProvider): String {
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
                    true -> ".toInt() ?: 0"
                    else -> ".toInt()"
                }
            ShapeType.BLOB ->
                if (targetShape.hasTrait(StreamingTrait::class.java)) {
                    // ByteStream
                    ".hashCode() ?: 0"
                } else {
                    // ByteArray
                    ".contentHashCode()"
                }
            else ->
                when (targetSymbol.isBoxed()) {
                    true -> ".hashCode() ?: 0"
                    else -> ".hashCode()"
                }
        }
    }

    // generate a `equals()` implementation
    private fun renderEquals(model: Model, sortedMembers: List<MemberShape>, typeName: String, writer: CodeWriter) {
        writer.write("")
        writer.withBlock("override fun equals(other: Any?): Boolean {", "}") {
            write("if (this === other) return true")
            write("if (javaClass != other?.javaClass) return false")
            write("")
            write("other as $typeName")
            write("")

            for (memberShape in sortedMembers) {
                val target = model.expectShape(memberShape.target)
                val memberName = "value"
                if (target is BlobShape && !target.hasTrait(StreamingTrait::class.java)) {
                    writer.write("if (!\$1L.contentEquals(other.\$1L)) return false", memberName)
                } else {
                    write("if (\$1L != other.\$1L) return false", memberName)
                }
            }

            write("")
            write("return true")
        }
    }

    private fun renderToStringOverride() {
        // override to string to use the union constant value
        writer.write("override fun toString(): kotlin.String = value")
    }
}
