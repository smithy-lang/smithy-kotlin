/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.serde

import aws.smithy.kotlin.codegen.core.KotlinWriter
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
    private val members: List<MemberShape> = shape.serdeMembers(model)
    private val names = MemberSchemaNames(model, symbolProvider, shape)
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
            val target = model.expectShape(member.target)
            val name = symbolProvider.toMemberName(member)
            if (symbolProvider.toSymbol(member).isNullable) {
                withBlock("this@#L.#L?.let {", "}", className, name) {
                    renderWriteValue(names[member], target, "it")
                }
            } else {
                renderWriteValue(names[member], target, "this@$className.$name")
            }
        }
    }

    private fun renderUnionBody(writer: KotlinWriter): Unit = with(writer) {
        // each branch reads `value` off the smart-cast receiver: an explicit cast would be redundant, and the
        // generated SDK is compiled with -Werror
        withBlock("when (this@#L) {", "}", className) {
            members.forEach { member ->
                writeInline("is #L.#L -> ", className, member.unionVariantName())
                renderWriteValue(names[member], model.expectShape(member.target), "this@$className.value")
            }
            write("is #L.SdkUnknown -> {}", className)
        }
    }

    // [bound] carries the lambda parameters of the enclosing collection scopes so that a nested collection does not
    // shadow them
    private fun KotlinWriter.renderWriteValue(schemaConst: String, target: Shape, accessor: String, bound: Set<String> = emptySet()) {
        when (target) {
            is StructureShape, is UnionShape -> write("writeStruct(#L, #L)", schemaConst, accessor)
            is ListShape -> withBlock("writeList(#L, #L.size) {", "}", schemaConst, accessor) {
                val elemTarget = model.expectShape(target.member.target)
                val sparse = target.hasTrait<SparseTrait>()
                val elem = scopedName("elem", bound)
                withBlock("#L.forEach { #L ->", "}", accessor, elem) {
                    renderCollectionSlot(names.element(schemaConst), elemTarget, elem, sparse, bound + elem)
                }
            }
            is MapShape -> withBlock("writeMap(#L, #L.size) {", "}", schemaConst, accessor) {
                val keyTarget = model.expectShape(target.key.target)
                val valTarget = model.expectShape(target.value.target)
                val sparse = target.hasTrait<SparseTrait>()
                val key = scopedName("k", bound)
                val value = scopedName("v", bound)
                withBlock("#L.forEach { (#L, #L) ->", "}", accessor, key, value) {
                    withBlock("entry(#L, #L) {", "}", names.key(schemaConst), keyTarget.wireValueExpr(key)) {
                        renderCollectionSlot(names.value(schemaConst), valTarget, value, sparse, bound + key + value)
                    }
                }
            }
            else -> write("#L(#L, #L)", target.writeFn, schemaConst, target.wireValueExpr(accessor))
        }
    }

    private fun KotlinWriter.renderCollectionSlot(schemaConst: String, target: Shape, ref: String, sparse: Boolean, bound: Set<String>) {
        if (sparse) {
            withBlock("if (#L == null) writeNull(#L) else {", "}", ref, schemaConst) {
                renderWriteValue(schemaConst, target, ref, bound)
            }
        } else {
            renderWriteValue(schemaConst, target, ref, bound)
        }
    }

    private fun scopedName(base: String, bound: Set<String>): String {
        var name = base
        var ordinal = 2
        while (name in bound) {
            name = "$base$ordinal"
            ordinal++
        }
        return name
    }
}
