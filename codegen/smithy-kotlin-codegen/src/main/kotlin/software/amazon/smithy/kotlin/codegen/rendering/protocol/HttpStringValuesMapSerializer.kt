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
 * Shared implementation to generate serialization for members bound to HTTP query parameters or headers. These
 * locations are represented by different data structures:
 * * **query parameters**: `MutableMultiMap`
 * * **headers**: `ValuesMapBuilder`
 *
 * This is a partial generator, the entry point for rendering from this component is an open block where the current
 * value of `this` one of the types listed above.
 *
 * Example output this class generates:
 * ```kotlin
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
            val addFnName = location.addFnName
            val member = it.member
            val memberSymbol = symbolProvider.toSymbol(member)
            when {
                memberTarget is CollectionShape -> renderCollectionShape(it, memberTarget, writer)
                memberTarget is TimestampShape -> {
                    val tsFormat = resolver.determineTimestampFormat(member, location, defaultTimestampFormat)
                    // headers/query params need to be a string
                    val formatted = formatInstant("input.$memberName", tsFormat, forceString = true)
                    val addFn = writer.format("#L(#S, #L)", addFnName, paramName, formatted)
                    writer.addImport(RuntimeTypes.Core.TimestampFormat)
                    writer.writeWithCondIfCheck(memberSymbol.isNullable, "input.$memberName != null", addFn)
                }
                memberTarget is BlobShape -> {
                    val addFn = writer.format(
                        "#L(#S, input.#L.#T())",
                        addFnName,
                        paramName,
                        memberName,
                        RuntimeTypes.Core.Text.Encoding.encodeBase64String,
                    )
                    writer.writeWithCondIfCheck(memberSymbol.isNullable, "input.$memberName?.isNotEmpty() == true", addFn)
                }
                memberTarget.isEnum -> {
                    val toString = if (memberTarget.isIntEnumShape) ".toString()" else ""
                    val addFn = writer.format("#L(#S, input.#L.value#L)", addFnName, paramName, memberName, toString)
                    if (memberSymbol.isNullable) {
                        writer.write("if (input.$memberName != null) $addFn")
                    } else {
                        val defaultCheck = defaultCheck(member) ?: ""
                        writer.writeWithCondIfCheck(defaultCheck.isNotEmpty(), defaultCheck, addFn)
                    }
                }
                memberTarget is StringShape -> renderStringShape(it, memberTarget, writer)
                else -> {
                    // encode to string
                    val encodedValue = "input.$memberName.toString()"
                    val addFn = writer.format("#L(#S, #L)", addFnName, paramName, encodedValue)
                    if (memberSymbol.isNullable) {
                        writer.write("if (input.$memberName != null) $addFn")
                    } else {
                        val defaultCheck = defaultCheck(member) ?: ""
                        writer.writeWithCondIfCheck(defaultCheck.isNotEmpty(), defaultCheck, addFn)
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
        val mapFnContents = when (collectionMemberTarget) {
            is TimestampShape -> {
                // special case of timestamp list
                val tsFormat = resolver.determineTimestampFormat(binding.member, binding.location, defaultTimestampFormat)
                writer.addImport(RuntimeTypes.Core.TimestampFormat)
                // headers/query params need to be a string
                formatInstant("it", tsFormat, forceString = true)
            }
            is StringShape -> {
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
            is IntEnumShape -> "\"\${it.value}\""
            // default to "toString"
            else -> "it.toString()"
        }

        val memberName = symbolProvider.toMemberName(binding.member)
        val memberSymbol = symbolProvider.toSymbol(binding.member)
        val paramName = binding.locationName
        // addAll collection parameter 2
        val param2 = if (mapFnContents.isEmpty()) "input.$memberName" else "input.$memberName.map { $mapFnContents }"
        val nullCheck = if (memberSymbol.isNullable) "?" else ""
        writer.write(
            "if (input.#L$nullCheck.isNotEmpty() == true) #L(#S, #L)",
            memberName,
            binding.location.addAllFnName,
            paramName,
            param2,
        )
    }

    private fun renderStringShape(binding: HttpBindingDescriptor, memberTarget: StringShape, writer: KotlinWriter) {
        val memberName = symbolProvider.toMemberName(binding.member)
        val location = binding.location
        val addFnName = location.addFnName
        val paramName = binding.locationName
        val memberSymbol = symbolProvider.toSymbol(binding.member)

        // NOTE: query parameters are allowed to be empty, whereas headers should omit empty string
        // values from serde
        if ((location == HttpBinding.Location.QUERY || location == HttpBinding.Location.HEADER) && binding.member.hasTrait<IdempotencyTokenTrait>()) {
            // Call the idempotency token function if no supplied value.
            writer.addImport(RuntimeTypes.SmithyClient.IdempotencyTokenProviderExt)
            writer.write(
                "#L(#S, (input.$memberName ?: context.idempotencyTokenProvider.generateToken()))",
                addFnName,
                paramName,
            )
        } else {
            val nullCheck =
                if (location == HttpBinding.Location.QUERY ||
                    memberTarget.hasTrait<
                        @Suppress("DEPRECATION")
                        software.amazon.smithy.model.traits.EnumTrait,
                        >()
                ) {
                    if (memberSymbol.isNullable) "input.$memberName != null" else ""
                } else {
                    val nullCheck = if (memberSymbol.isNullable) "?" else ""
                    "input.$memberName$nullCheck.isNotEmpty() == true"
                }

            val cond = defaultCheck(binding.member) ?: nullCheck

            val suffix = when {
                memberTarget.hasTrait<
                    @Suppress("DEPRECATION")
                    software.amazon.smithy.model.traits.EnumTrait,
                    >() -> {
                    ".value"
                }
                memberTarget.hasTrait<MediaTypeTrait>() -> {
                    writer.addImport(RuntimeTypes.Core.Text.Encoding.encodeBase64)
                    ".encodeBase64()"
                }
                else -> ""
            }

            val addFn = writer.format("#L(#S, #L)", addFnName, paramName, "input.${memberName}$suffix")
            writer.writeWithCondIfCheck(cond.isNotEmpty(), cond, addFn)
        }
    }
}

private val HttpBinding.Location.addFnName: String
    get() = when (this) {
        HttpBinding.Location.QUERY, HttpBinding.Location.QUERY_PARAMS -> "add" // uses MutableMultiMap
        else -> "append" // uses ValuesMapBuilder
    }

private val HttpBinding.Location.addAllFnName: String
    get() = "${addFnName}All"
