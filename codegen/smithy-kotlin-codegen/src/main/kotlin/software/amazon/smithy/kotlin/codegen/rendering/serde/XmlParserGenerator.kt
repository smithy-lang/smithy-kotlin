/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes.Serde
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes.Serde.SerdeXml
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.model.*
import software.amazon.smithy.kotlin.codegen.model.knowledge.SerdeIndex
import software.amazon.smithy.kotlin.codegen.model.traits.UnwrappedXmlOutput
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.SparseTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.model.traits.XmlAttributeTrait
import software.amazon.smithy.model.traits.XmlFlattenedTrait
import software.amazon.smithy.model.traits.XmlNameTrait
import software.amazon.smithy.utils.StringUtils

/**
 * XML parser generator based on common deserializer interface and XML serde descriptors
 */
open class XmlParserGenerator(
    private val defaultTimestampFormat: TimestampFormatTrait.Format,
) : StructuredDataParserGenerator {

    /**
     * Deserialization context that holds current state
     * @param tagReader the name of the current tag reader to operate on
     */
    data class SerdeCtx(
        val tagReader: String,
    )

    override fun operationDeserializer(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        members: List<MemberShape>,
    ): Symbol {
        val outputSymbol = ctx.symbolProvider.toSymbol(ctx.model.expectShape(op.output.get()))
        return op.bodyDeserializer(ctx.settings) { writer ->
            addNestedDocumentDeserializers(ctx, op, writer)
            val fnName = op.bodyDeserializerName()
            writer.openBlock("private fun #L(builder: #T.Builder, payload: ByteArray) {", fnName, outputSymbol)
                .call {
                    renderDeserializeOperationBody(ctx, op, members, writer)
                }
                .closeBlock("}")
        }
    }

    /**
     * Register nested structure/map shapes reachable from the operation input shape that require a "document" deserializer
     * implementation
     */
    protected fun addNestedDocumentDeserializers(ctx: ProtocolGenerator.GenerationContext, shape: Shape, writer: KotlinWriter) {
        val serdeIndex = SerdeIndex.of(ctx.model)
        val shapesRequiringDocumentDeserializer = serdeIndex.requiresDocumentDeserializer(shape)
        // register a dependency on each of the members that require a deserializer impl
        // ensuring they get generated
        shapesRequiringDocumentDeserializer.forEach {
            val nestedStructOrUnionDeserializer = documentDeserializer(ctx, it)
            writer.addImport(nestedStructOrUnionDeserializer)
        }
    }

    protected open fun renderDeserializeOperationBody(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        documentMembers: List<MemberShape>,
        writer: KotlinWriter,
    ) {
        writer.write("val root = #T(payload)", SerdeXml.xmlTagReader)
        val shape = ctx.model.expectShape(op.output.get())
        val serdeCtx = unwrapOperationBody(ctx, SerdeCtx("root"), op, writer)

        if (op.hasTrait<UnwrappedXmlOutput>()) {
            renderDeserializerUnwrappedXmlBody(ctx, serdeCtx, shape, writer)
        } else {
            renderDeserializerBody(ctx, serdeCtx, shape, documentMembers, writer)
        }
    }

    /**
     * Hook for protocols to perform logic prior to deserializing the operation output.
     * Implementations must return the [SerdeCtx] to use for further deserialization.
     */
    protected open fun unwrapOperationBody(
        ctx: ProtocolGenerator.GenerationContext,
        serdeCtx: SerdeCtx,
        op: OperationShape,
        writer: KotlinWriter,
    ): SerdeCtx = serdeCtx

    protected fun renderDeserializerBody(
        ctx: ProtocolGenerator.GenerationContext,
        serdeCtx: SerdeCtx,
        shape: Shape,
        members: List<MemberShape>,
        writer: KotlinWriter,
    ) {
        if (shape.isUnionShape) {
            deserializeUnion(ctx, serdeCtx, members, writer)
        } else {
            deserializeStruct(ctx, serdeCtx, members, writer)
        }
    }

    private fun documentDeserializer(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: Collection<MemberShape> = shape.members(),
    ): Symbol {
        val symbol = ctx.symbolProvider.toSymbol(shape)
        return shape.documentDeserializer(ctx.settings, symbol, members) { writer ->
            writer.openBlock("internal fun #identifier.name:L(reader: #T): #T {", SerdeXml.TagReader, symbol)
                .call {
                    val serdeCtx = SerdeCtx("reader")
                    if (shape.isUnionShape) {
                        writer.write("var value: #T? = null", symbol)
                        renderDeserializerBody(ctx, serdeCtx, shape, members.toList(), writer)
                        writer.write("return value ?: throw #T(#S)", Serde.DeserializationException, "Deserialized union value unexpectedly null: ${symbol.name}")
                    } else {
                        writer.write("val builder = #T.Builder()", symbol)
                        renderDeserializerBody(ctx, serdeCtx, shape, members.toList(), writer)
                        writer.write("builder.correctErrors()")
                        writer.write("return builder.build()")
                    }
                }
                .closeBlock("}")
        }
    }

    override fun errorDeserializer(
        ctx: ProtocolGenerator.GenerationContext,
        errorShape: StructureShape,
        members: List<MemberShape>,
    ): Symbol {
        val symbol = ctx.symbolProvider.toSymbol(errorShape)
        return symbol.errorDeserializer(ctx.settings) { writer ->
            addNestedDocumentDeserializers(ctx, errorShape, writer)
            val fnName = symbol.errorDeserializerName()
            writer.openBlock("internal fun #L(builder: #T.Builder, payload: ByteArray) {", fnName, symbol)
                .call {
                    writer.write("val root = #T(payload)", SerdeXml.xmlTagReader)
                    val serdeCtx = unwrapOperationError(ctx, SerdeCtx("root"), errorShape, writer)
                    renderDeserializerBody(ctx, serdeCtx, errorShape, members, writer)
                }
                .closeBlock("}")
        }
    }

    /**
     * Hook for protocols to perform logic prior to deserializing an operation error.
     * Implementations must return the [SerdeCtx] to use for further deserialization.
     */
    protected open fun unwrapOperationError(
        ctx: ProtocolGenerator.GenerationContext,
        serdeCtx: SerdeCtx,
        errorShape: StructureShape,
        writer: KotlinWriter,
    ): SerdeCtx = serdeCtx

    override fun payloadDeserializer(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: Collection<MemberShape>?,
    ): Symbol {
        // re-use document deserializer
        val target = shape.targetOrSelf(ctx.model)
        val symbol = ctx.symbolProvider.toSymbol(shape)
        val forMembers = members ?: target.members()
        val deserializeFn = documentDeserializer(ctx, target, forMembers)
        return target.payloadDeserializer(ctx.settings, symbol, forMembers) { writer ->
            addNestedDocumentDeserializers(ctx, target, writer)
            writer.withBlock("internal fun #identifier.name:L(payload: ByteArray): #T {", "}", symbol) {
                if (target.members().isEmpty()) {
                    // short circuit when the shape has no modeled members to deserialize
                    write("return #T.Builder().build()", symbol)
                } else {
                    writer.write("val root = #T(payload)", SerdeXml.xmlTagReader)
                    write("return #T(root)", deserializeFn)
                }
            }
        }
    }

    private fun KotlinWriter.deserializeLoop(
        serdeCtx: SerdeCtx,
        ignoreUnexpected: Boolean = true,
        block: KotlinWriter.(SerdeCtx) -> Unit,
    ) {
        withBlock("loop@while(true) {", "}") {
            write("val curr = ${serdeCtx.tagReader}.nextTag() ?: break@loop")
            withBlock("when(curr.tag.name.tag) {", "}") {
                block(this, serdeCtx.copy(tagReader = "curr"))
                if (ignoreUnexpected) {
                    write("else -> {}")
                }
            }
            // maintain stream reader state by dropping the current element and all it's children
            // this ensures nested elements with potentially the same name as a higher level element
            // are not erroneously returned and matched by `nextTag()`
            write("curr.drop()")
        }
    }

    private fun deserializeUnion(
        ctx: ProtocolGenerator.GenerationContext,
        serdeCtx: SerdeCtx,
        members: List<MemberShape>,
        writer: KotlinWriter,
    ) {
        writer.deserializeLoop(serdeCtx) { innerCtx ->
            members.forEach { member ->
                val name = member.getTrait<XmlNameTrait>()?.value ?: member.memberName
                write("// ${member.memberName} ${escape(member.id.toString())}")
                val unionTypeName = member.unionTypeName(ctx)
                withBlock("#S -> value = #L(", ")", name, unionTypeName) {
                    deserializeMember(ctx, innerCtx, member, writer)
                }
            }
        }
    }

    private fun deserializeStruct(
        ctx: ProtocolGenerator.GenerationContext,
        serdeCtx: SerdeCtx,
        members: List<MemberShape>,
        writer: KotlinWriter,
    ) {
        // split attribute members and non attribute members
        val attributeMembers = members.filter { it.hasTrait<XmlAttributeTrait>() }
        attributeMembers.forEach { member ->
            deserializeAttributeMember(ctx, serdeCtx, member, writer)
        }

        val payloadMembers = members.filterNot { it.hasTrait<XmlAttributeTrait>() }
        // don't generate a parse loop if no attribute members
        if (payloadMembers.isEmpty()) return
        writer.write("")
        writer.deserializeLoop(serdeCtx) { innerCtx ->
            payloadMembers.forEach { member ->
                val name = member.getTrait<XmlNameTrait>()?.value ?: member.memberName
                write("// ${member.memberName} ${escape(member.id.toString())}")
                writeInline("#S -> builder.#L = ", name, member.defaultName())
                deserializeMember(ctx, innerCtx, member, writer)
            }
        }
    }

    private fun deserializeAttributeMember(
        ctx: ProtocolGenerator.GenerationContext,
        serdeCtx: SerdeCtx,
        member: MemberShape,
        writer: KotlinWriter,
    ) {
        val memberName = member.getTrait<XmlNameTrait>()?.value ?: member.memberName
        writer.withBlock(
            "${serdeCtx.tagReader}.tag.getAttr(#S)?.let {",
            "}",
            memberName,
        ) {
            writeInline("builder.#L = ", member.defaultName())
            deserializePrimitiveMember(ctx, member, "it", textExprIsResult = false, this)
        }
    }

    private fun deserializeMember(
        ctx: ProtocolGenerator.GenerationContext,
        serdeCtx: SerdeCtx,
        member: MemberShape,
        writer: KotlinWriter,
    ) {
        val target = ctx.model.expectShape(member.target)
        when (target.type) {
            ShapeType.LIST, ShapeType.SET -> {
                if (member.hasTrait<XmlFlattenedTrait>()) {
                    deserializeFlatList(ctx, serdeCtx, member, writer)
                } else {
                    deserializeList(ctx, serdeCtx, member, writer)
                }
            }
            ShapeType.MAP -> {
                if (member.hasTrait<XmlFlattenedTrait>()) {
                    deserializeFlatMap(ctx, serdeCtx, member, writer)
                } else {
                    deserializeMap(ctx, serdeCtx, member, writer)
                }
            }
            ShapeType.STRUCTURE, ShapeType.UNION -> {
                val deserializeFn = documentDeserializer(ctx, target)
                writer.write("#T(${serdeCtx.tagReader})", deserializeFn)
            }
            else -> deserializePrimitiveMember(
                ctx,
                member,
                writer.format("${serdeCtx.tagReader}.#T()", SerdeXml.tryData),
                textExprIsResult = true,
                writer,
            )
        }
    }

    // TODO - this could probably be moved to SerdeExt and commonized

    private fun Shape.shapeDeserializerDefinitionFile(
        ctx: ProtocolGenerator.GenerationContext,
    ): String {
        val target = targetOrSelf(ctx.model)
        val shapeName = StringUtils.capitalize(target.id.getName(ctx.service))
        return "${shapeName}ShapeDeserializer.kt"
    }
    private fun Shape.shapeDeserializer(
        ctx: ProtocolGenerator.GenerationContext,
        block: (fnName: String, writer: KotlinWriter) -> Unit,
    ): Symbol {
        val target = targetOrSelf(ctx.model)
        val shapeName = StringUtils.capitalize(target.id.getName(ctx.service))
        val symbol = ctx.symbolProvider.toSymbol(this)

        val fnName = "deserialize${shapeName}Shape"
        return buildSymbol {
            name = fnName
            namespace = ctx.settings.pkg.serde
            definitionFile = shapeDeserializerDefinitionFile(ctx)
            reference(symbol, SymbolReference.ContextOption.DECLARE)
            renderBy = {
                block(fnName, it)
            }
        }
    }

    private fun deserializeShape(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        block: KotlinWriter.() -> Unit,
    ): Symbol {
        val symbol = ctx.symbolProvider.toSymbol(shape)
        val deserializeFn = shape.shapeDeserializer(ctx) { fnName, writer ->
            writer.withBlock(
                "internal fun #L(reader: #T): #T {",
                "}",
                fnName,
                SerdeXml.TagReader,
                symbol,
            ) {
                block(this)
            }
        }
        return deserializeFn
    }

    private fun deserializeList(
        ctx: ProtocolGenerator.GenerationContext,
        serdeCtx: SerdeCtx,
        member: MemberShape,
        writer: KotlinWriter,
    ) {
        val target = ctx.model.expectShape<CollectionShape>(member.target)
        val targetMember = target.member
        val isSparse = target.hasTrait<SparseTrait>()
        val deserializeFn = deserializeShape(ctx, target) {
            write("val result = mutableListOf<#T#L>()", ctx.symbolProvider.toSymbol(targetMember), nullabilitySuffix(isSparse))
            deserializeLoop(SerdeCtx(tagReader = "reader")) { innerCtx ->
                val memberName = targetMember.getTrait<XmlNameTrait>()?.value ?: targetMember.memberName
                withBlock("#S -> {", "}", memberName) {
                    deserializeListInner(ctx, innerCtx, target, this)
                    write("result.add(el)")
                }
            }
            write("return result")
        }
        writer.write("#T(${serdeCtx.tagReader})", deserializeFn)
    }

    private fun flatCollectionAccumulatorExpr(
        ctx: ProtocolGenerator.GenerationContext,
        member: MemberShape,
    ): String =
        when (val container = ctx.model.expectShape(member.container)) {
            is StructureShape -> "builder.${member.defaultName()}"
            is UnionShape -> {
                val unionVariantName = member.unionVariantName()
                "value?.as${unionVariantName}OrNull()"
            }
            else -> error("unexpected container shape $container for member $member")
        }

    private fun deserializeFlatList(
        ctx: ProtocolGenerator.GenerationContext,
        serdeCtx: SerdeCtx,
        member: MemberShape,
        writer: KotlinWriter,
    ) {
        val target = ctx.model.expectShape<CollectionShape>(member.target)
        writer.withBlock("run {", "}") {
            deserializeListInner(ctx, serdeCtx, target, this)
            val accum = flatCollectionAccumulatorExpr(ctx, member)
            write("#T(#L, el)", RuntimeTypes.Core.Collections.createOrAppend, accum)
        }
    }

    private fun deserializeListInner(
        ctx: ProtocolGenerator.GenerationContext,
        serdeCtx: SerdeCtx,
        target: CollectionShape,
        writer: KotlinWriter,
    ) {
        // <member></member> <- sparse
        // <member>CDATA || TAG(s)</member> <- not sparse
        val isSparse = target.hasTrait<SparseTrait>()
        with(writer) {
            if (isSparse) {
                openBlock("val el = if (${serdeCtx.tagReader}.nextHasValue()) {")
                    .call {
                        deserializeMember(ctx, serdeCtx, target.member, this)
                    }
                    .closeAndOpenBlock("} else {")
                    .write("null")
                    .closeBlock("}")
            } else {
                writeInline("val el = ")
                deserializeMember(ctx, serdeCtx, target.member, this)
            }
        }
    }

    private fun deserializeMap(
        ctx: ProtocolGenerator.GenerationContext,
        serdeCtx: SerdeCtx,
        member: MemberShape,
        writer: KotlinWriter,
    ) {
        val target = ctx.model.expectShape<MapShape>(member.target)
        val keySymbol = ctx.symbolProvider.toSymbol(target.key)
        val valueSymbol = ctx.symbolProvider.toSymbol(target.value)
        writer.addImportReferences(valueSymbol, SymbolReference.ContextOption.USE)
        val isSparse = target.hasTrait<SparseTrait>()

        val deserializeFn = deserializeShape(ctx, target) {
            write("val result = mutableMapOf<#T, #T#L>()", keySymbol, valueSymbol, nullabilitySuffix(isSparse))
            deserializeLoop(SerdeCtx("reader")) { innerCtx ->
                withBlock("#S -> {", "}", "entry") {
                    val deserializeEntryFn = deserializeMapEntry(ctx, target)
                    write("#T(result, ${innerCtx.tagReader})", deserializeEntryFn)
                }
            }
            write("return result")
        }
        writer.write("#T(${serdeCtx.tagReader})", deserializeFn)
    }
    private fun deserializeFlatMap(
        ctx: ProtocolGenerator.GenerationContext,
        serdeCtx: SerdeCtx,
        member: MemberShape,
        writer: KotlinWriter,
    ) {
        val target = ctx.model.expectShape<MapShape>(member.target)
        val keySymbol = ctx.symbolProvider.toSymbol(target.key)
        val valueSymbol = ctx.symbolProvider.toSymbol(target.value)
        val isSparse = target.hasTrait<SparseTrait>()
        writer.addImportReferences(valueSymbol, SymbolReference.ContextOption.USE)
        writer.withBlock("run {", "}") {
            val accum = flatCollectionAccumulatorExpr(ctx, member)
            write(
                "val dest = #L?.toMutableMap() ?: mutableMapOf<#T, #T#L>()",
                accum,
                keySymbol,
                valueSymbol,
                nullabilitySuffix(isSparse),
            )
            val deserializeEntryFn = deserializeMapEntry(ctx, target)
            write("#T(dest, ${serdeCtx.tagReader})", deserializeEntryFn)
            write("dest")
        }
    }

    private fun deserializeMapEntry(
        ctx: ProtocolGenerator.GenerationContext,
        map: MapShape,
    ): Symbol {
        val shapeName = StringUtils.capitalize(map.id.getName(ctx.service))
        val keySymbol = ctx.symbolProvider.toSymbol(map.key)
        val valueSymbol = ctx.symbolProvider.toSymbol(map.value)
        val isSparse = map.hasTrait<SparseTrait>()
        val serdeCtx = SerdeCtx("reader")

        return buildSymbol {
            name = "deserialize${shapeName}Entry"
            namespace = ctx.settings.pkg.serde
            definitionFile = map.shapeDeserializerDefinitionFile(ctx)
            renderBy = { writer ->
                // NOTE: we make this internal rather than private because flat maps don't generate a
                // dedicated map deserializer, they inline the entry deserialization since the map
                // being built up is not processed all at once
                writer.withBlock(
                    "internal fun $name(dest: #T<#T, #T#L>, reader: #T) {",
                    "}",
                    KotlinTypes.Collections.MutableMap,
                    keySymbol,
                    valueSymbol,
                    nullabilitySuffix(isSparse),
                    SerdeXml.TagReader,
                ) {
                    write("var key: #T? = null", keySymbol)
                    write("var value: #T? = null", valueSymbol)
                    writer.addImportReferences(valueSymbol, SymbolReference.ContextOption.USE)
                    deserializeLoop(serdeCtx) { innerCtx ->
                        val keyName = map.key.getTrait<XmlNameTrait>()?.value ?: map.key.memberName
                        writeInline("#S -> key = ", keyName)
                        deserializeMember(ctx, innerCtx, map.key, this)

                        val valueName = map.value.getTrait<XmlNameTrait>()?.value ?: map.value.memberName
                        if (isSparse) {
                            openBlock("#S -> value = if (${innerCtx.tagReader}.nextHasValue()) {", valueName)
                                .call {
                                    deserializeMember(ctx, innerCtx, map.value, this)
                                }
                                .closeAndOpenBlock("} else {")
                                .write("null")
                                .closeBlock("}")
                        } else {
                            writeInline("#S -> value = ", valueName)
                            deserializeMember(ctx, innerCtx, map.value, this)
                        }
                    }
                    write("if (key == null) throw #T(#S)", Serde.DeserializationException, "missing key map entry")
                    if (!isSparse) {
                        write("if (value == null) throw #T(#S)", Serde.DeserializationException, "missing value map entry")
                    }
                    write("dest[key] = value")
                }
            }
        }
    }

    private fun deserializePrimitiveMember(
        ctx: ProtocolGenerator.GenerationContext,
        member: MemberShape,
        textExpr: String,
        textExprIsResult: Boolean,
        writer: KotlinWriter,
    ) {
        val target = ctx.model.expectShape(member.target)

        val parseFn = when (target.type) {
            ShapeType.BLOB -> writer.format("#T { it.#T() } ", Serde.parse, RuntimeTypes.Core.Text.Encoding.decodeBase64Bytes)
            ShapeType.BOOLEAN -> writer.format("#T()", Serde.parseBoolean)
            ShapeType.STRING -> {
                if (!textExprIsResult) {
                    writer.write(textExpr)
                    return
                } else {
                    null
                }
            }
            ShapeType.TIMESTAMP -> {
                val trait = member.getTrait<TimestampFormatTrait>() ?: target.getTrait()
                val tsFormat = trait?.format ?: defaultTimestampFormat
                // val fromArg = writer.format("curr.#T()")
                // val fmtExpr = writer.parseInstantExpr(fromArg, tsFormat)
                // writer.write(fmtExpr)
                val runtimeEnum = tsFormat.toRuntimeEnum(writer)
                writer.format("#T(#L)", Serde.parseTimestamp, runtimeEnum)
            }
            ShapeType.BYTE -> writer.format("#T()", Serde.parseByte)
            ShapeType.SHORT -> writer.format("#T()", Serde.parseShort)
            ShapeType.INTEGER -> writer.format("#T()", Serde.parseInt)
            ShapeType.LONG -> writer.format("#T()", Serde.parseLong)
            ShapeType.FLOAT -> writer.format("#T()", Serde.parseFloat)
            ShapeType.DOUBLE -> writer.format("#T()", Serde.parseDouble)
            ShapeType.BIG_DECIMAL -> writer.format("#T()", Serde.parseBigDecimal)
            ShapeType.BIG_INTEGER -> writer.format("#T()", Serde.parseBigInteger)
            ShapeType.ENUM -> {
                if (!textExprIsResult) {
                    writer.write("#T.fromValue(#L)", ctx.symbolProvider.toSymbol(target), textExpr)
                    return
                }
                writer.format("#T { #T.fromValue(it) } ", Serde.parse, ctx.symbolProvider.toSymbol(target))
            }
            ShapeType.INT_ENUM -> {
                writer.format("#T { #T.fromValue(it.toInt()) } ", Serde.parse, ctx.symbolProvider.toSymbol(target))
            }
            else -> error("unknown primitive member shape $member")
        }

        val escapedErrMessage = "expected $target".replace("$", "$$")
        writer.write(textExpr)
            .indent()
            .callIf(parseFn != null) {
                writer.write(".#L", parseFn)
            }
            .write(".#T { #S }", Serde.getOrDeserializeErr, escapedErrMessage)
            .dedent()
    }

    private fun renderDeserializerUnwrappedXmlBody(
        ctx: ProtocolGenerator.GenerationContext,
        serdeCtx: SerdeCtx,
        shape: Shape,
        writer: KotlinWriter,
    ) {
        val members = shape.members()
        check(members.size == 1) {
            "unwrapped XML output trait is only allowed on operation output structs with exactly one member"
        }

        val member = members.first()
        writer.withBlock("when(${serdeCtx.tagReader}.tag.name.tag) {", "}") {
            val name = member.getTrait<XmlNameTrait>()?.value ?: member.memberName
            write("// ${member.memberName} ${escape(member.id.toString())}")
            writeInline("#S -> builder.#L = ", name, member.defaultName())
            deserializeMember(ctx, serdeCtx, member, writer)
        }
    }
}
