/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.serde

import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.core.RuntimeTypes.Serde.Schema
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
    private val members: List<MemberShape> = shape.serdeMembers(model)
    private val names = MemberSchemaNames(model, symbolProvider, shape)
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
                        renderReadValue(names[member], model.expectShape(member.target), "d", "member")
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
                        renderReadValue(names[member], model.expectShape(member.target), "d", "member")
                        write(")")
                    }
                    write("else -> {}")
                }
            }
            write("return value ?: #L.SdkUnknown", className)
        }
    }

    private fun KotlinWriter.renderReadValue(schemaConst: String, target: Shape, de: String, memberSchemaRef: String, depth: Int = 0) {
        when (target) {
            is StructureShape -> writeInline("#T.Builder().apply { deserialize(#L) }.build()", symbolProvider.toSymbol(target), de)
            is UnionShape -> writeInline("#T.deserialize(#L)", symbolProvider.toSymbol(target), de)
            is ListShape -> {
                val elemTarget = model.expectShape(target.member.target)
                val sparse = target.hasTrait<SparseTrait>()
                val out = local("out", depth)
                val list = local("list", depth)
                val elem = local("e", depth)
                withInlineBlock("run {", "}") {
                    write("")
                    write("val #L = #T<#L>()", out, KotlinTypes.Collections.mutableListOf, target.member.kotlinTypeName(symbolProvider))
                    withBlock("#L.readList(#L, #L) { #L, #L ->", "}", de, schemaConst, out, list, elem) {
                        renderReadCollectionSlot(names.element(schemaConst), elemTarget, elem, sparse, depth) { "$list.add($it)" }
                    }
                    write("#L", out)
                }
            }
            is MapShape -> {
                val keyTarget = model.expectShape(target.key.target)
                val valTarget = model.expectShape(target.value.target)
                val sparse = target.hasTrait<SparseTrait>()
                val out = local("out", depth)
                val map = local("map", depth)
                val elem = local("e", depth)
                // the wire key is always a string, so an enum-keyed map widens each key on the way into the map
                val keyExpr = keyTarget.kotlinValueExpr(symbolProvider, local("k", depth))
                withInlineBlock("run {", "}") {
                    write(
                        "val #L = #T<#L, #L>()",
                        out,
                        KotlinTypes.Collections.mutableMapOf,
                        target.key.kotlinTypeName(symbolProvider),
                        target.value.kotlinTypeName(symbolProvider),
                    )
                    withBlock("#L.readMap(#L, #L) { #L, #L, #L ->", "}", de, schemaConst, out, map, local("k", depth), elem) {
                        renderReadCollectionSlot(names.value(schemaConst), valTarget, elem, sparse, depth) { "$map[$keyExpr] = $it" }
                    }
                    write("#L", out)
                }
            }
            else -> writeInline("#L", target.readValueExpr(symbolProvider, de, memberSchemaRef))
        }
    }

    private fun KotlinWriter.renderReadCollectionSlot(
        schemaConst: String,
        target: Shape,
        de: String,
        sparse: Boolean,
        depth: Int,
        collect: (String) -> String,
    ) {
        val value = local("v", depth)
        if (sparse) {
            withBlock("if (#L.isNull()) {", "}", de) { write(collect("null")) }
            withBlock("else {", "}") {
                writeInline("val #L = ", value)
                renderReadValue(schemaConst, target, de, schemaConst, depth + 1)
                ensureNewline()
                write(collect(value))
            }
        } else {
            writeInline("val #L = ", value)
            renderReadValue(schemaConst, target, de, schemaConst, depth + 1)
            ensureNewline()
            write(collect(value))
        }
    }

    private fun local(name: String, depth: Int): String = if (depth == 0) name else "$name$depth"
}
