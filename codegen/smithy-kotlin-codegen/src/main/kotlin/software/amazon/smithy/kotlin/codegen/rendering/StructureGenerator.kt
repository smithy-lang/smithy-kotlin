/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.model.*
import software.amazon.smithy.kotlin.codegen.rendering.serde.ClientErrorCorrection
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.*

/**
 * Renders Smithy structure shapes
 */
class StructureGenerator(
    private val ctx: RenderingContext<StructureShape>,
) {
    private val shape = requireNotNull(ctx.shape)
    private val writer = ctx.writer
    private val symbolProvider = ctx.symbolProvider
    private val model = ctx.model
    private val symbol = ctx.symbolProvider.toSymbol(ctx.shape)

    fun render() {
        writer.renderDocumentation(shape)
        writer.renderAnnotations(shape)
        if (!shape.isError) {
            renderStructure()
        } else {
            renderError()
        }
    }

    private val sortedMembers: List<MemberShape> = shape.allMembers.values.sortedBy { it.defaultName() }
    private val memberNameSymbolIndex: Map<MemberShape, Pair<String, Symbol>> =
        sortedMembers.associateWith { member ->
            Pair(symbolProvider.toMemberName(member), symbolProvider.toSymbol(member))
        }

    /**
     * Renders a normal (non-error) Smithy structure to a Kotlin class
     */
    private fun renderStructure() {
        writer.openBlock(
            "#L class #T private constructor(builder: Builder) {",
            ctx.settings.api.visibility,
            symbol,
        )
            .call { renderImmutableProperties() }
            .write("")
            .call { renderCompanionObject() }
            .call { renderToString() }
            .call { renderHashCode() }
            .call { renderEquals() }
            .call { renderCopy() }
            .call { renderBuilder() }
            .closeBlock("}")
            .write("")
    }

    private fun renderImmutableProperties() {
        // generate the immutable properties that are set from a builder
        sortedMembers.forEach {
            val (memberName, memberSymbol) = memberNameSymbolIndex[it]!!
            // Throwable.message is handled special and passed as a constructor parameter to the parent exception base class
            if (shape.isError && memberName == "message") {
                val targetShape = model.expectShape(it.target)
                if (!targetShape.isStringShape) {
                    throw CodegenException("message is a reserved name for exception types and cannot be used for any other property")
                }
                return@forEach
            }
            writer.renderMemberDocumentation(model, it)
            writer.renderAnnotations(it)
            renderImmutableProperty(memberName, memberSymbol)
        }
    }

    private fun renderImmutableProperty(memberName: String, memberSymbol: Symbol) {
        if (memberSymbol.isRequiredWithNoDefault) {
            writer.write(
                """public val #1L: #2F = requireNotNull(builder.#1L) { "A non-null value must be provided for #1L" }""",
                memberName,
                memberSymbol,
            )
        } else {
            writer.write("public val #1L: #2F = builder.#1L", memberName, memberSymbol)
        }
    }

    private fun renderCompanionObject() {
        writer.withBlock("public companion object {", "}") {
            write("public operator fun invoke(block: Builder.() -> #Q): #Q = Builder().apply(block).build()", KotlinTypes.Unit, symbol)
        }
    }

    // generate a `toString()` implementation
    private fun renderToString() {
        writer.write("")
        writer.withBlock("override fun toString(): #Q = buildString {", "}", KotlinTypes.String) {
            write("append(\"#T(\")", symbol)

            when {
                shape.hasTrait<SensitiveTrait>() -> write("append(#S)", "*** Sensitive Data Redacted ***")
                else -> {
                    sortedMembers.forEachIndexed { index, memberShape ->
                        val (memberName, _) = memberNameSymbolIndex[memberShape]!!
                        val separator = if (index < sortedMembers.size - 1) "," else ""

                        val targetShape = model.expectShape(memberShape.target)
                        if (targetShape.hasTrait<SensitiveTrait>()) {
                            write("append(\"#1L=*** Sensitive Data Redacted ***$separator\")", memberName)
                        } else {
                            write("append(\"#1L=\$#2L$separator\")", memberShape.defaultName(), memberName)
                        }
                    }
                }
            }

            write("append(\")\")")
        }
    }

    // generate a `hashCode()` implementation
    private fun renderHashCode() {
        writer.write("")
        writer.withBlock("override fun hashCode(): #Q {", "}", KotlinTypes.Int) {
            when {
                sortedMembers.isEmpty() -> write("return this::class.hashCode()")
                else -> {
                    write("var result = #1L#2L", memberNameSymbolIndex[sortedMembers[0]]!!.first, selectHashFunctionForShape(sortedMembers[0]))
                    if (sortedMembers.size > 1) {
                        sortedMembers.drop(1).forEach { memberShape ->
                            write("result = 31 * result + (#1L#2L)", memberNameSymbolIndex[memberShape]!!.first, selectHashFunctionForShape(memberShape))
                        }
                    }
                    write("return result")
                }
            }
        }
    }

    // Return the appropriate hashCode fragment based on ShapeID of member target.
    private fun selectHashFunctionForShape(member: MemberShape): String {
        val targetShape = model.expectShape(member.target)
        val isNullable = memberNameSymbolIndex[member]!!.second.isNullable
        return when (targetShape.type) {
            ShapeType.INTEGER ->
                when (isNullable) {
                    true -> " ?: 0"
                    else -> ""
                }
            ShapeType.BYTE ->
                when (isNullable) {
                    true -> "?.toInt() ?: 0"
                    else -> ".toInt()"
                }
            ShapeType.BLOB -> {
                val hashFn = if (targetShape.hasTrait<StreamingTrait>()) {
                    // ByteStream
                    "hashCode()"
                } else {
                    // ByteArray
                    "contentHashCode()"
                }
                when (isNullable) {
                    true -> "?.$hashFn ?: 0"
                    false -> ".$hashFn"
                }
            }
            else ->
                when (isNullable) {
                    true -> "?.hashCode() ?: 0"
                    else -> ".hashCode()"
                }
        }
    }

    // generate a `equals()` implementation
    private fun renderEquals() {
        writer.write("")
        writer.withBlock("override fun equals(other: #Q?): #Q {", "}", KotlinTypes.Any, KotlinTypes.Boolean) {
            write("if (this === other) return true")
            write("if (other == null || this::class != other::class) return false")
            write("")
            write("other as #T", symbol)
            write("")

            for (memberShape in sortedMembers) {
                val target = model.expectShape(memberShape.target)
                val memberName = memberNameSymbolIndex[memberShape]!!.first
                if (target is BlobShape && !target.hasTrait<StreamingTrait>()) {
                    openBlock("if (#1L != null) {", memberName)
                        .write("if (other.#1L == null) return false", memberName)
                        .write("if (!#1L.contentEquals(other.#1L)) return false", memberName)
                        .closeBlock("} else if (other.#1L != null) return false", memberName)
                } else {
                    write("if (#1L != other.#1L) return false", memberName)
                }
            }

            write("")
            write("return true")
        }
    }

    // generate a `copy()` implementation
    private fun renderCopy() {
        if (sortedMembers.isEmpty()) return

        // copy has to go through a builder, if we were to generate a "normal"
        // data class copy() with defaults for all properties we would end up in the same
        // situation we have with constructors and positional arguments not playing well
        // with models evolving over time (e.g. new fields in different positions)
        writer.write("")
            .write("public inline fun copy(block: Builder.() -> #Q = {}): #Q = Builder(this).apply(block).build()", KotlinTypes.Unit, symbol)
            .write("")
    }

    private fun renderBuilder() {
        writer.write("")
            .write("@#T", RuntimeTypes.Core.SdkDsl)
            .withBlock("public class Builder {", "}") {
                for (member in sortedMembers) {
                    val (memberName, memberSymbol) = memberNameSymbolIndex[member]!!
                    writer.renderMemberDocumentation(model, member)
                    writer.renderAnnotations(member)
                    val builderMemberSymbol = if (memberSymbol.isRequiredWithNoDefault) {
                        // nullabilty is w.r.t to the immmutable property type, builders have to account for the user
                        // providing required values though and thus must be nullable. They are then checked
                        // at runtime in the ctor to ensure a value was provided
                        memberSymbol.toBuilder().nullable().build()
                    } else {
                        memberSymbol
                    }
                    write("public var #L: #E", memberName, builderMemberSymbol)
                }
                write("")

                // generate the constructor used internally by serde
                write("@PublishedApi")
                write("internal constructor()")
                // generate the constructor that converts from the underlying immutable class to a builder instance
                writer.write("@PublishedApi")
                withBlock("internal constructor(x: #Q) : this() {", "}", symbol) {
                    for (member in sortedMembers) {
                        val (memberName, _) = memberNameSymbolIndex[member]!!
                        write("this.#1L = x.#1L", memberName)
                    }
                }

                write("")
                write("@PublishedApi")
                write("internal fun build(): #1Q = #1T(this)", symbol)

                val structMembers = sortedMembers.filter { member ->
                    val targetShape = model.getShape(member.target).get()
                    targetShape.isStructureShape
                }

                for (member in structMembers) {
                    writer.write("")
                    val (memberName, memberSymbol) = memberNameSymbolIndex[member]!!
                    writer.dokka("construct an [${memberSymbol.fullName}] inside the given [block]")
                    writer.renderAnnotations(member)
                    openBlock("public fun #L(block: #Q.Builder.() -> #Q) {", memberName, memberSymbol, KotlinTypes.Unit)
                        .write("this.#L = #Q.invoke(block)", memberName, memberSymbol)
                        .closeBlock("}")
                }

                write("")

                // render client side error correction function to set @required members to a default
                withBlock(
                    "internal fun correctErrors(): Builder {",
                    "}",
                ) {
                    sortedMembers
                        .filter {
                            val (_, memberSymbol) = memberNameSymbolIndex[it]!!
                            // required members with no default
                            memberSymbol.isRequiredWithNoDefault
                        }
                        .filterNot {
                            val target = ctx.model.expectShape(it.target)
                            target.isStreaming || it.hasTrait<ClientOptionalTrait>()
                        }
                        .forEach {
                            val correctedValue = ClientErrorCorrection.defaultValue(ctx, it, writer)
                            write("if (#1L == null) #1L = #2L", ctx.symbolProvider.toMemberName(it), correctedValue)
                        }
                    write("return this")
                }
            }
    }

    /**
     * Renders a Smithy error type to a Kotlin exception type
     */
    private fun renderError() {
        val errorTrait: ErrorTrait = shape.expectTrait()
        val (isRetryable, isThrottling) = shape
            .getTrait<RetryableTrait>()
            ?.let { true to it.throttling }
            ?: (false to false)

        checkForConflictsInHierarchy()

        val exceptionBaseClass = ExceptionBaseClassGenerator.baseExceptionSymbol(ctx.settings)
        writer.addImport(exceptionBaseClass)

        val superParam = shape.members().find {
            symbolProvider.toMemberName(it) == "message"
        }?.let { "builder.message" } ?: ""

        writer.openBlock(
            "#L class #T private constructor(builder: Builder) : #L(#L) {",
            ctx.settings.api.visibility,
            symbol,
            exceptionBaseClass.name,
            superParam,
        )
            .write("")
            .call { renderImmutableProperties() }
            .write("")
            .withBlock("init {", "}") {
                // initialize error metadata
                if (isRetryable) {
                    call { renderRetryable(isThrottling) }
                }
                call { renderErrorType(errorTrait) }
            }
            .write("")
            .call { renderCompanionObject() }
            .call { renderToString() }
            .call { renderHashCode() }
            .call { renderEquals() }
            .call { renderCopy() }
            .call { renderBuilder() }
            .closeBlock("}")
            .write("")
    }

    private fun renderRetryable(isThrottling: Boolean) {
        writer.write("sdkErrorMetadata.attributes[ErrorMetadata.Retryable] = true")
        writer.write("sdkErrorMetadata.attributes[ErrorMetadata.ThrottlingError] = #L", isThrottling)
        writer.addImport(RuntimeTypes.Core.ErrorMetadata)
    }

    private fun renderErrorType(errorTrait: ErrorTrait) {
        val errorType = when {
            errorTrait.isClientError -> "ErrorType.Client"
            errorTrait.isServerError -> "ErrorType.Server"
            else -> {
                throw CodegenException("Errors must be either of client or server type")
            }
        }
        writer.write("sdkErrorMetadata.attributes[ServiceErrorMetadata.ErrorType] = $errorType")
        writer.addImport(RuntimeTypes.Core.ServiceErrorMetadata)
    }

    // throw an exception if there are conflicting property names between the error structure and properties inherited
    // from the base class
    private fun checkForConflictsInHierarchy() {
        val baseExceptionProperties = setOf("sdkErrorMetadata")
        val hasConflictWithBaseClass = sortedMembers.map {
            symbolProvider.toMemberName(it)
        }.any { it in baseExceptionProperties }

        if (hasConflictWithBaseClass) throw CodegenException("`sdkErrorMetadata` conflicts with property of same name inherited from SdkBaseException. Apply a rename customization/projection to fix.")
    }
}
