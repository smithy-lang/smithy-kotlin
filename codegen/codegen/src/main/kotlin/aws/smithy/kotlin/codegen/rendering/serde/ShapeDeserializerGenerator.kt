/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.serde

import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.core.RuntimeTypes.Serde.Schema
import aws.smithy.kotlin.codegen.core.defaultName
import aws.smithy.kotlin.codegen.core.unionVariantName
import aws.smithy.kotlin.codegen.core.withBlock
import aws.smithy.kotlin.codegen.core.withInlineBlock
import aws.smithy.kotlin.codegen.lang.KotlinTypes
import aws.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ListShape
import software.amazon.smithy.model.shapes.MapShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.SparseTrait

internal class ShapeDeserializerGenerator(
    private val model: Model,
    private val symbolProvider: SymbolProvider,
    private val shape: Shape,
) {
    // sorted by Kotlin member name to match the property/argument order used elsewhere in the class
    private val members: List<MemberShape> = (
        (shape as? StructureShape)?.members() ?: (shape as UnionShape).members()
        ).sortedBy { it.defaultName() }
    private val className: String = symbolProvider.toSymbol(shape).name

    fun render(writer: KotlinWriter) {
        if (shape is UnionShape) renderUnion(writer) else renderStruct(writer)
    }

    private fun renderStruct(writer: KotlinWriter) {
        writer.write("")
        writer.withBlock("override fun deserialize(deserializer: #T): #Q {", "}", Schema.Serde.ShapeDeserializer, KotlinTypes.Unit) {
            withBlock("deserializer.readStruct(SCHEMA, this) { builder, member, d ->", "}") {
                withBlock("when (member.memberName) {", "}") {
                    members.forEach { member ->
                        writeInline("#S -> builder.#L = ", member.memberName, symbolProvider.toMemberName(member))
                        renderReadValue(member.constName, model.expectShape(member.target), "d", "member")
                        ensureNewline()
                    }
                    write("else -> {}")
                }
            }
        }
    }

    private fun renderUnion(writer: KotlinWriter) {
        writer.write("")
        writer.withBlock("public fun deserialize(deserializer: #T): #T {", "}", Schema.Serde.ShapeDeserializer, symbolProvider.toSymbol(shape)) {
            write("var value: #T? = null", symbolProvider.toSymbol(shape))
            withBlock("deserializer.readStruct(SCHEMA, Unit) { _, member, d ->", "}") {
                withBlock("when (member.memberName) {", "}") {
                    members.forEach { member ->
                        writeInline("#S -> value = #L.#L(", member.memberName, className, member.unionVariantName())
                        renderReadValue(member.constName, model.expectShape(member.target), "d", "member")
                        write(")")
                    }
                    write("else -> {}")
                }
            }
            write("return value ?: #L.SdkUnknown", className)
        }
    }

    private fun KotlinWriter.renderReadValue(schemaConst: String, target: Shape, de: String, memberSchemaRef: String) {
        when (target) {
            is StructureShape -> writeInline("#T.Builder().apply { deserialize(#L) }.build()", symbolProvider.toSymbol(target), de)
            is UnionShape -> writeInline("#T.deserialize(#L)", symbolProvider.toSymbol(target), de)
            is ListShape -> {
                val elemTarget = model.expectShape(target.member.target)
                val sparse = target.hasTrait<SparseTrait>()
                withInlineBlock("run {", "}") {
                    write("")
                    write("val out = #T<#L>()", KotlinTypes.Collections.mutableListOf, elementTypeName(elemTarget, sparse))
                    withBlock("#L.readList(#L, out) { list, e ->", "}", de, schemaConst) {
                        renderReadCollectionSlot("${schemaConst}_ELEMENT", elemTarget, "e", sparse) { "list.add($it)" }
                    }
                    write("out")
                }
            }
            is MapShape -> {
                val valTarget = model.expectShape(target.value.target)
                val sparse = target.hasTrait<SparseTrait>()
                withInlineBlock("run {", "}") {
                    write("")
                    write("val out = #T<#Q, #L>()", KotlinTypes.Collections.mutableMapOf, KotlinTypes.String, elementTypeName(valTarget, sparse))
                    withBlock("#L.readMap(#L, out) { map, k, e ->", "}", de, schemaConst) {
                        renderReadCollectionSlot("${schemaConst}_VALUE", valTarget, "e", sparse) { "map[k] = $it" }
                    }
                    write("out")
                }
            }
            else -> writeInline("#L.#L(#L)", de, target.readFn, memberSchemaRef)
        }
    }

    private fun KotlinWriter.renderReadCollectionSlot(schemaConst: String, target: Shape, de: String, sparse: Boolean, collect: (String) -> String) {
        if (sparse) {
            withBlock("if (#L.isNull()) {", "}", de) { write(collect("null")) }
            withBlock("else {", "}") {
                writeInline("val v = ")
                renderReadValue(schemaConst, target, de, schemaConst)
                ensureNewline()
                write(collect("v"))
            }
        } else {
            writeInline("val v = ")
            renderReadValue(schemaConst, target, de, schemaConst)
            ensureNewline()
            write(collect("v"))
        }
    }

    private fun elementTypeName(target: Shape, sparse: Boolean): String {
        val sym = symbolProvider.toSymbol(target)
        val base = if (sym.namespace.isEmpty()) sym.name else "${sym.namespace}.${sym.name}"
        return if (sparse) "$base?" else base
    }
}
