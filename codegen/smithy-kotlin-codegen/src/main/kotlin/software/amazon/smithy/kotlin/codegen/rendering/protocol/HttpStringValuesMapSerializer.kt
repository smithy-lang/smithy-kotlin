/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.protocol

import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.DefaultValueSerializationMode
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.model.*
import software.amazon.smithy.kotlin.codegen.rendering.serde.formatInstant
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.IdempotencyTokenTrait
import software.amazon.smithy.model.traits.MediaTypeTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.utils.AbstractCodeWriter

/**
 * Shared implementation to generate serialization for members bound to HTTP query parameters or headers
 * (both of which are implemented using `ValuesMap<String>`).
 *
 * This is a partial generator, the entry point for rendering from this component is an open block where the current
 * value of `this` is a `ValuesMapBuilder<String>`.
 *
 * Example output this class generates:
 * ```
 * if (input.field1 != null) append("X-Foo", input.field1)
 * if (input.field2?.isNotEmpty() == true) appendAll("X-Foo", input.field2!!.map { it.value })
 * ```
 */
class HttpStringValuesMapSerializer(
    private val model: Model,
    private val symbolProvider: SymbolProvider,
    private val settings: KotlinSettings,
    private val bindings: List<HttpBindingDescriptor>,
    private val resolver: HttpBindingResolver,
    private val defaultTimestampFormat: TimestampFormatTrait.Format,
) {
    constructor(
        ctx: ProtocolGenerator.GenerationContext,
        bindings: List<HttpBindingDescriptor>,
        resolver: HttpBindingResolver,
        defaultTimestampFormat: TimestampFormatTrait.Format,
    ) : this(ctx.model, ctx.symbolProvider, ctx.settings, bindings, resolver, defaultTimestampFormat)

    fun render(
        writer: KotlinWriter,
    ) {
        bindings.sortedBy(HttpBindingDescriptor::memberName).forEach {
            val memberName = symbolProvider.toMemberName(it.member)
            val memberTarget = model.expectShape(it.member.target)
            val paramName = it.locationName
            val location = it.location
            val member = it.member
            val memberSymbol = symbolProvider.toSymbol(member)
            when (memberTarget) {
                is CollectionShape -> renderCollectionShape(it, memberTarget, writer)
                is TimestampShape -> {
                    val tsFormat = resolver.determineTimestampFormat(member, location, defaultTimestampFormat)
                    // headers/query params need to be a string
                    val formatted = formatInstant("input.$memberName", tsFormat, forceString = true)
                    val appendFn = writer.format("append(#S, #L)", paramName, formatted)
                    writer.addImport(RuntimeTypes.Core.TimestampFormat)
                    writer.writeWithCondIfCheck(memberSymbol.isNullable, "input.$memberName != null", appendFn)
                }
                is BlobShape -> {
                    val appendFn = writer.format(
                        "append(#S, input.#L.#T()",
                        paramName,
                        memberName,
                        RuntimeTypes.Core.Text.Encoding.encodeBase64String,
                    )
                    writer.writeWithCondIfCheck(memberSymbol.isNullable, "input.$memberName?.isNotEmpty() == true", appendFn)
                }
                is StringShape -> renderStringShape(it, memberTarget, writer)
                is IntEnumShape -> {
                    val appendFn = writer.format("append(#S, \"\${input.#L.value}\")", paramName, memberName)
                    if (memberSymbol.isNullable) {
                        writer.write("if (input.$memberName != null) $appendFn")
                    } else {
                        val defaultCheck = defaultCheck(member) ?: ""
                        writer.writeWithCondIfCheck(defaultCheck.isNotEmpty(), defaultCheck, appendFn)
                    }
                }
                else -> {
                    // encode to string
                    val encodedValue = "\"\${input.$memberName}\""
                    val appendFn = writer.format("append(#S, #L)", paramName, encodedValue)
                    if (memberSymbol.isNullable) {
                        writer.write("if (input.$memberName != null) $appendFn")
                    } else {
                        val defaultCheck = defaultCheck(member) ?: ""
                        writer.writeWithCondIfCheck(defaultCheck.isNotEmpty(), defaultCheck, appendFn)
                    }
                }
            }
        }
    }
    private fun defaultCheck(member: MemberShape): String? {
        val memberSymbol = symbolProvider.toSymbol(member)
        val memberName = symbolProvider.toMemberName(member)
        val defaultValue = memberSymbol.defaultValue()
        val checkDefaults = settings.api.defaultValueSerializationMode == DefaultValueSerializationMode.WHEN_DIFFERENT
        val check = "input.$memberName != $defaultValue"
        return check.takeIf { checkDefaults && !member.isRequired && memberSymbol.isNotNullable && defaultValue != null }
    }

    private fun AbstractCodeWriter<*>.writeWithCondIfCheck(cond: Boolean, check: String, body: String) {
        if (cond) {
            write("if ($check) $body")
        } else {
            write(body)
        }
    }

    private fun renderCollectionShape(binding: HttpBindingDescriptor, memberTarget: CollectionShape, writer: KotlinWriter) {
        val collectionMemberTarget = model.expectShape(memberTarget.member.target)
        val mapFnContents = when (collectionMemberTarget.type) {
            ShapeType.TIMESTAMP -> {
                // special case of timestamp list
                val tsFormat = resolver.determineTimestampFormat(binding.member, binding.location, defaultTimestampFormat)
                writer.addImport(RuntimeTypes.Core.TimestampFormat)
                // headers/query params need to be a string
                formatInstant("it", tsFormat, forceString = true)
            }
            ShapeType.STRING -> {
                when (binding.location) {
                    HttpBinding.Location.QUERY -> if (collectionMemberTarget.isEnum) "it.value" else ""
                    else -> {
                        // collections of enums should be mapped to the raw values
                        val inner = if (collectionMemberTarget.isEnum) "it.value" else "it"
                        // ensure header values targeting lists are quoted appropriately
                        val quoteHeaderValue = RuntimeTypes.Http.Util.quoteHeaderValue
                        writer.addImport(quoteHeaderValue)
                        "${quoteHeaderValue.name}($inner)"
                    }
                }
            }
            ShapeType.INT_ENUM -> "\"\${it.value}\""
            // default to "toString"
            else -> "\"\$it\""
        }

        val memberName = symbolProvider.toMemberName(binding.member)
        val memberSymbol = symbolProvider.toSymbol(binding.member)
        val paramName = binding.locationName
        // appendAll collection parameter 2
        val param2 = if (mapFnContents.isEmpty()) "input.$memberName" else "input.$memberName.map { $mapFnContents }"
        val nullCheck = if (memberSymbol.isNullable) "?" else ""
        writer.write(
            "if (input.#1L$nullCheck.isNotEmpty() == true) appendAll(#2S, #3L)",
            memberName,
            paramName,
            param2,
        )
    }

    private fun renderStringShape(binding: HttpBindingDescriptor, memberTarget: StringShape, writer: KotlinWriter) {
        val memberName = symbolProvider.toMemberName(binding.member)
        val location = binding.location
        val paramName = binding.locationName
        val memberSymbol = symbolProvider.toSymbol(binding.member)

        // NOTE: query parameters are allowed to be empty, whereas headers should omit empty string
        // values from serde
        if ((location == HttpBinding.Location.QUERY || location == HttpBinding.Location.HEADER) && binding.member.hasTrait<IdempotencyTokenTrait>()) {
            // Call the idempotency token function if no supplied value.
            writer.addImport(RuntimeTypes.SmithyClient.IdempotencyTokenProviderExt)
            writer.write("append(#S, (input.$memberName ?: context.idempotencyTokenProvider.generateToken()))", paramName)
        } else {
            val nullCheck =
                if (location == HttpBinding.Location.QUERY ||
                    memberTarget.hasTrait<@Suppress("DEPRECATION") software.amazon.smithy.model.traits.EnumTrait>()
                ) {
                    if (memberSymbol.isNullable) "input.$memberName != null" else ""
                } else {
                    val nullCheck = if (memberSymbol.isNullable) "?" else ""
                    "input.$memberName$nullCheck.isNotEmpty() == true"
                }

            val cond = defaultCheck(binding.member) ?: nullCheck

            val suffix = when {
                memberTarget.hasTrait<@Suppress("DEPRECATION") software.amazon.smithy.model.traits.EnumTrait>() -> {
                    ".value"
                }
                memberTarget.hasTrait<MediaTypeTrait>() -> {
                    writer.addImport(RuntimeTypes.Core.Text.Encoding.encodeBase64)
                    ".encodeBase64()"
                }
                else -> ""
            }

            val appendFn = writer.format("append(#S, #L)", paramName, "input.${memberName}$suffix")
            writer.writeWithCondIfCheck(cond.isNotEmpty(), cond, appendFn)
        }
    }
}
