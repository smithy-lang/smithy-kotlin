/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.serde

import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.core.defaultName
import aws.smithy.kotlin.codegen.core.RuntimeTypes.Serde.Schema
import aws.smithy.kotlin.codegen.core.unionVariantName
import aws.smithy.kotlin.codegen.core.withBlock
import aws.smithy.kotlin.codegen.lang.KotlinTypes
import aws.smithy.kotlin.codegen.model.hasTrait
import aws.smithy.kotlin.codegen.model.isNullable
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ListShape
import software.amazon.smithy.model.shapes.MapShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.SparseTrait

internal class ShapeSerializerGenerator(
    private val model: Model,
    private val symbolProvider: SymbolProvider,
    private val shape: Shape,
) {
    private val members: List<MemberShape> = (
        (shape as? StructureShape)?.members() ?: (shape as UnionShape).members()
        ).sortedBy { it.defaultName() }
    private val className: String = symbolProvider.toSymbol(shape).name

    fun render(writer: KotlinWriter) {
        writer.write("")
        writer.write(
            "override fun serialize(serializer: #T<*>): #Q = serializer.writeStruct(SCHEMA) { serializeMembers(this) }",
            Schema.Serde.ShapeSerializer,
            KotlinTypes.Unit,
        )
        writer.withBlock("override fun serializeMembers(dest: #T): #Q = with(dest) {", "}", Schema.Serde.StructSerializer, KotlinTypes.Unit) {
            if (shape is UnionShape) renderUnionBody(this) else renderStructBody(this)
        }
    }

    private fun renderStructBody(writer: KotlinWriter): Unit = with(writer) {
        members.forEach { member ->
            val name = symbolProvider.toMemberName(member)
            if (symbolProvider.toSymbol(member).isNullable) {
                withBlock("this@#L.#L?.let {", "}", className, name) {
                    renderWriteValue(member.constName, model.expectShape(member.target), "it")
                }
            } else {
                renderWriteValue(member.constName, model.expectShape(member.target), "this@$className.$name")
            }
        }
    }

    private fun renderUnionBody(writer: KotlinWriter): Unit = with(writer) {
        withBlock("when (this@#L) {", "}", className) {
            members.forEach { member ->
                val variant = member.unionVariantName()
                writeInline("is #L.#L -> ", className, variant)
                renderWriteValue(member.constName, model.expectShape(member.target), "(this@$className as $className.$variant).value")
            }
            write("is #L.SdkUnknown -> {}", className)
        }
    }

    private fun KotlinWriter.renderWriteValue(schemaConst: String, target: Shape, accessor: String) {
        when (target) {
            is StructureShape, is UnionShape -> write("writeStruct(#L, #L)", schemaConst, accessor)
            is ListShape -> withBlock("writeList(#L, #L.size) {", "}", schemaConst, accessor) {
                val elemTarget = model.expectShape(target.member.target)
                val sparse = target.hasTrait<SparseTrait>()
                withBlock("#L.forEach { elem ->", "}", accessor) {
                    renderCollectionSlot("${schemaConst}_ELEMENT", elemTarget, "elem", sparse)
                }
            }
            is MapShape -> withBlock("writeMap(#L, #L.size) {", "}", schemaConst, accessor) {
                val valTarget = model.expectShape(target.value.target)
                val sparse = target.hasTrait<SparseTrait>()
                withBlock("#L.forEach { (k, v) ->", "}", accessor) {
                    withBlock("entry(#L_KEY, k) {", "}", schemaConst) {
                        renderCollectionSlot("${schemaConst}_VALUE", valTarget, "v", sparse)
                    }
                }
            }
            else -> write("#L(#L, #L)", target.writeFn, schemaConst, accessor)
        }
    }

    private fun KotlinWriter.renderCollectionSlot(schemaConst: String, target: Shape, ref: String, sparse: Boolean) {
        if (sparse) {
            withBlock("if (#L == null) writeNull(#L) else {", "}", ref, schemaConst) {
                renderWriteValue(schemaConst, target, ref)
            }
        } else {
            renderWriteValue(schemaConst, target, ref)
        }
    }
}
