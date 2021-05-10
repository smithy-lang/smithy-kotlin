/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering

import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.model.isBoxed
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
        check(!shape.allMembers.values.any { memberShape -> memberShape.memberName.equals("SdkUnknown", true) }) { "generating SdkUnknown would cause duplicate variant for union shape: $shape" }
        val symbol = symbolProvider.toSymbol(shape)
        writer.renderDocumentation(shape)
        writer.renderAnnotations(shape)
        writer.openBlock("sealed class #T {", symbol)
        shape.allMembers.values.sortedBy { it.memberName }.forEach {
            writer.renderMemberDocumentation(model, it)
            writer.renderAnnotations(it)
            val variantName = it.unionVariantName()
            val targetType = model.expectShape(it.target).type
            writer.writeInline("data class #L(val value: #Q) : #Q()", variantName, symbolProvider.toSymbol(it), symbol)
            when (targetType) {
                ShapeType.BLOB -> {
                    writer.withBlock(" {", "}") {
                        renderHashCode(model, listOf(it), symbolProvider, this)
                        renderEquals(model, listOf(it), variantName, this)
                    }
                }
                else -> writer.write("")
            }
        }
        // generate the unknown which will always be last
        writer.write("object SdkUnknown : #Q()", symbol)
        writer.closeBlock("}").write("")
    }

    // generate a `hashCode()` implementation
    private fun renderHashCode(model: Model, sortedMembers: List<MemberShape>, symbolProvider: SymbolProvider, writer: CodeWriter) {
        writer.write("")
        writer.withBlock("override fun hashCode(): #Q {", "}", KotlinTypes.Int) {
            write("return value#L", selectHashFunctionForShape(model, sortedMembers[0], symbolProvider))
        }
    }

    // Return the appropriate hashCode fragment based on ShapeID of member target.
    private fun selectHashFunctionForShape(model: Model, member: MemberShape, symbolProvider: SymbolProvider): String {
        val targetShape = model.expectShape(member.target)
        // also available already in the byMember map
        val targetSymbol = symbolProvider.toSymbol(targetShape)

        return when (targetShape.type) {
            ShapeType.INTEGER ->
                when (targetSymbol.isBoxed) {
                    true -> " ?: 0"
                    else -> ""
                }
            ShapeType.BYTE ->
                when (targetSymbol.isBoxed) {
                    true -> ".toInt() ?: 0"
                    else -> ".toInt()"
                }
            ShapeType.BLOB ->
                if (targetShape.hasTrait<StreamingTrait>()) {
                    // ByteStream
                    ".hashCode() ?: 0"
                } else {
                    // ByteArray
                    ".contentHashCode()"
                }
            else ->
                when (targetSymbol.isBoxed) {
                    true -> ".hashCode() ?: 0"
                    else -> ".hashCode()"
                }
        }
    }

    // generate a `equals()` implementation
    private fun renderEquals(model: Model, sortedMembers: List<MemberShape>, typeName: String, writer: CodeWriter) {
        writer.write("")
        writer.withBlock("override fun equals(other: #Q?): #Q {", "}", KotlinTypes.Any, KotlinTypes.Boolean) {
            write("if (this === other) return true")
            write("if (javaClass != other?.javaClass) return false")
            write("")
            write("other as $typeName")
            write("")

            for (memberShape in sortedMembers) {
                val target = model.expectShape(memberShape.target)
                val memberName = "value"
                if (target is BlobShape && !target.hasTrait<StreamingTrait>()) {
                    writer.write("if (!#1L.contentEquals(other.#1L)) return false", memberName)
                } else {
                    write("if (#1L != other.#1L) return false", memberName)
                }
            }

            write("")
            write("return true")
        }
    }
}
