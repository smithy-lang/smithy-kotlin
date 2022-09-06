/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.protocol

import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.core.KotlinDependency
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.addImport
import software.amazon.smithy.kotlin.codegen.model.*
import software.amazon.smithy.kotlin.codegen.rendering.serde.formatInstant
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.IdempotencyTokenTrait
import software.amazon.smithy.model.traits.MediaTypeTrait
import software.amazon.smithy.model.traits.RequiredTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait

/**
 * Shared implementation to generate serialization for members bound to HTTP query parameters or headers
 * (both of which are implemented using `StringValuesMap`).
 *
 * This is a partial generator, the entry point for rendering from this component is an open block where the current
 * value of `this` is a `StringValuesMapBuilder`.
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
    private val bindings: List<HttpBindingDescriptor>,
    private val resolver: HttpBindingResolver,
    private val defaultTimestampFormat: TimestampFormatTrait.Format,
) {
    constructor(
        ctx: ProtocolGenerator.GenerationContext,
        bindings: List<HttpBindingDescriptor>,
        resolver: HttpBindingResolver,
        defaultTimestampFormat: TimestampFormatTrait.Format,
    ) : this(ctx.model, ctx.symbolProvider, bindings, resolver, defaultTimestampFormat)

    fun render(
        writer: KotlinWriter,
    ) {
        bindings.sortedBy(HttpBindingDescriptor::memberName).forEach {
            val memberName = symbolProvider.toMemberName(it.member)
            val memberTarget = model.expectShape(it.member.target)
            val paramName = it.locationName
            val location = it.location
            val member = it.member
            when (memberTarget) {
                is CollectionShape -> renderCollectionShape(it, memberTarget, writer)
                is TimestampShape -> {
                    val tsFormat = resolver.determineTimestampFormat(member, location, defaultTimestampFormat)
                    // headers/query params need to be a string
                    val formatted = formatInstant("input.$memberName", tsFormat, forceString = true)
                    writer.write("if (input.#1L != null) append(\"#2L\", #3L)", memberName, paramName, formatted)
                    writer.addImport(RuntimeTypes.Core.TimestampFormat)
                }
                is BlobShape -> {
                    writer.addImport("encodeBase64String", KotlinDependency.UTILS)
                    writer.write(
                        "if (input.#1L?.isNotEmpty() == true) append(\"#2L\", input.#1L.encodeBase64String())",
                        memberName,
                        paramName,
                    )
                }
                is StringShape -> renderStringShape(it, memberTarget, writer)
                else -> {
                    // encode to string
                    val encodedValue = "\"\${input.$memberName}\""

                    val targetSymbol = symbolProvider.toSymbol(member)
                    val defaultValue = targetSymbol.defaultValue()
                    if ((memberTarget.isNumberShape || memberTarget.isBooleanShape) && targetSymbol.isNotBoxed && defaultValue != null) {
                        // unboxed primitive with a default value
                        if (member.hasTrait<RequiredTrait>()) {
                            // always serialize a required member even if it's the default
                            writer.write("append(#S, #L)", paramName, encodedValue)
                        } else {
                            writer.write("if (input.#1L != $defaultValue) append(#2S, #3L)", memberName, paramName, encodedValue)
                        }
                    } else {
                        writer.write("if (input.#1L != null) append(#2S, #3L)", memberName, paramName, encodedValue)
                    }
                }
            }
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
                    HttpBinding.Location.QUERY -> {
                        if (collectionMemberTarget.isEnum) "it.value" else ""
                    }
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
            // default to "toString"
            else -> "\"\$it\""
        }

        val memberName = symbolProvider.toMemberName(binding.member)
        val paramName = binding.locationName
        // appendAll collection parameter 2
        val param2 = if (mapFnContents.isEmpty()) "input.$memberName" else "input.$memberName.map { $mapFnContents }"
        writer.write(
            "if (input.#1L?.isNotEmpty() == true) appendAll(\"#2L\", #3L)",
            memberName,
            paramName,
            param2,
        )
    }

    private fun renderStringShape(binding: HttpBindingDescriptor, memberTarget: StringShape, writer: KotlinWriter) {
        val memberName = symbolProvider.toMemberName(binding.member)
        val location = binding.location
        val paramName = binding.locationName

        // NOTE: query parameters are allowed to be empty, whereas headers should omit empty string
        // values from serde
        if ((location == HttpBinding.Location.QUERY || location == HttpBinding.Location.HEADER) && binding.member.hasTrait<IdempotencyTokenTrait>()) {
            // Call the idempotency token function if no supplied value.
            writer.addImport(RuntimeTypes.Core.IdempotencyTokenProviderExt)
            writer.write("append(\"#L\", (input.$memberName ?: context.idempotencyTokenProvider.generateToken()))", paramName)
        } else {
            val cond =
                if (location == HttpBinding.Location.QUERY ||
                    memberTarget.hasTrait<@Suppress("DEPRECATION") software.amazon.smithy.model.traits.EnumTrait>()
                ) {
                    "input.$memberName != null"
                } else {
                    "input.$memberName?.isNotEmpty() == true"
                }

            val suffix = when {
                memberTarget.hasTrait<@Suppress("DEPRECATION") software.amazon.smithy.model.traits.EnumTrait>() -> {
                    ".value"
                }
                memberTarget.hasTrait<MediaTypeTrait>() -> {
                    writer.addImport("encodeBase64", KotlinDependency.UTILS)
                    ".encodeBase64()"
                }
                else -> ""
            }

            writer.write("if (#1L) append(\"#2L\", #3L)", cond, paramName, "input.${memberName}$suffix")
        }
    }
}
