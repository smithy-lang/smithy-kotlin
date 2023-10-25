/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.model

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.core.defaultName
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.model.traits.OperationInput
import software.amazon.smithy.kotlin.codegen.model.traits.OperationOutput
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.OperationIndex
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.*
import software.amazon.smithy.rulesengine.language.EndpointRuleSet
import software.amazon.smithy.rulesengine.traits.EndpointRuleSetTrait
import software.amazon.smithy.rulesengine.traits.EndpointTestCase
import software.amazon.smithy.rulesengine.traits.EndpointTestsTrait
import kotlin.streams.toList

/**
 * Get all shapes of a particular type from the model.
 * Equivalent to `model.shapes({ShapeType}::class.java)`.
 *
 * NOTE: this is usually not what you want as it will return all
 * shapes _loaded_ (traits, models on classpath, etc). You usually
 * want to iterate shapes within a predefined closure (the service
 * shape's closure for example)
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
inline fun <reified T : Shape> Model.shapes(): List<T> = shapes(T::class.java).toList()

/**
 * Extension function to return a shape of expected type.
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
inline fun <reified T : Shape> Model.expectShape(shapeId: ShapeId): T =
    expectShape(shapeId, T::class.java)

/**
 * Extension function to return a shape of expected type.
 */
inline fun <reified T : Shape> Model.expectShape(shapeId: String): T =
    this.expectShape(ShapeId.from(shapeId), T::class.java)

/**
 * Returns this shape's ID name with the suffix changed (if applicable). For example, given a shape with the name
 * "OperationResponse", the call `changeNameSuffix("Response" to "Result")` will return "OperationResult". Note that if
 * the given "from" is not the existing suffix then this method merely appends the "to" suffix.
 */
fun Shape.changeNameSuffix(fromTo: Pair<String, String>): String {
    val unsuffixed = id.name.removeSuffix(fromTo.first)
    return "$unsuffixed${fromTo.second}"
}

/**
 * If is member shape returns target, otherwise returns self.
 * @param model for loading the target shape
 */
fun Shape.targetOrSelf(model: Model): Shape = when (this) {
    is MemberShape -> model.expectShape(this.target)
    else -> this
}

/**
 * Kotlin sugar for hasTrait() check. e.g. shape.hasTrait<EnumTrait>() instead of shape.hasTrait(EnumTrait::class.java)
 */
inline fun <reified T : Trait> Shape.hasTrait(): Boolean = hasTrait(T::class.java)

/**
 * Kotlin sugar for expectTrait() check. e.g. shape.expectTrait<EnumTrait>() instead of shape.expectTrait(EnumTrait::class.java)
 */
inline fun <reified T : Trait> Shape.expectTrait(): T = expectTrait(T::class.java)

/**
 * Kotlin sugar for getTrait() check. e.g. shape.getTrait<EnumTrait>() instead of shape.getTrait(EnumTrait::class.java)
 */
inline fun <reified T : Trait> Shape.getTrait(): T? = getTrait(T::class.java).getOrNull()

fun StructureShape.hasStreamingMember(model: Model): Boolean = findStreamingMember(model) != null
fun UnionShape.hasStreamingMember(model: Model) = findMemberWithTrait<StreamingTrait>(model) != null

/*
 * Returns the member of this structure targeted with streaming trait (if it exists).
 *
 * A structure must have at most one streaming member.
 */
fun StructureShape.findStreamingMember(model: Model): MemberShape? = findMemberWithTrait<StreamingTrait>(model)

inline fun <reified T : Trait> StructureShape.findMemberWithTrait(model: Model): MemberShape? =
    members().find { it.getMemberTrait(model, T::class.java).isPresent }

inline fun <reified T : Trait> UnionShape.findMemberWithTrait(model: Model): MemberShape? =
    members().find { it.getMemberTrait(model, T::class.java).isPresent }

/**
 * Returns true if any operation bound to the service contains an input member marked with the IdempotencyTokenTrait
 */
fun ServiceShape.hasIdempotentTokenMember(model: Model): Boolean {
    val topDownIndex = TopDownIndex.of(model)
    return topDownIndex
        .getContainedOperations(id)
        .any { operation ->
            operation.input.isPresent &&
                model.expectShape(operation.input.get()).members().any { it.hasTrait(IdempotencyTokenTrait.ID.name) }
        }
}

/**
 * Return the formatted (Kotlin) function signature for the given operation
 * @param includeOptionalDefault Set whether the arg declaration should include a default initializer for operations
 * where the input has no required parameters. Consumers that render both a super and subclass definition using this
 * method will need to manage this parameter to ensure that the default is only present in the former.
 */
fun OperationIndex.operationSignature(
    model: Model,
    symbolProvider: SymbolProvider,
    op: OperationShape,
    includeOptionalDefault: Boolean = false,
): String {
    val inputShape = this.getInput(op)
    val outputShape = this.getOutput(op)
    val input = inputShape.map { symbolProvider.toSymbol(it).name }
    val output = outputShape.map { symbolProvider.toSymbol(it).name }

    val hasOutputStream = outputShape.map { it.hasStreamingMember(model) }.orElse(false)
    val inputParam = input.map {
        if (includeOptionalDefault && inputShape.get().hasAllOptionalMembers) "input: $it = $it { }" else "input: $it"
    }.orElse("")
    val outputParam = output.map { ": $it" }.orElse("")

    val operationName = op.defaultName()

    return if (!hasOutputStream) {
        "suspend fun $operationName($inputParam)$outputParam"
    } else {
        val outputName = output.get()
        val inputSignature = if (inputParam.isNotEmpty()) {
            "$inputParam, "
        } else {
            ""
        }
        "suspend fun <T> $operationName(${inputSignature}block: suspend ($outputName) -> T): T"
    }
}

/**
 * Test if a shape is deprecated.
 */
val Shape.isDeprecated: Boolean
    get() = hasTrait<DeprecatedTrait>()

/**
 * Test if a shape represents either kind of enumeration
 */
val Shape.isEnum: Boolean
    get() = isStringEnumShape || isIntEnumShape

/**
 * Test if a shape is a string-based enum, which will present either as:
 * 1. The explicit enum shape (NOT intEnum)
 * 2. The [legacy enum trait](https://awslabs.github.io/smithy/1.0/spec/core/constraint-traits.html#enum-trait) applied to a string shape
 */
val Shape.isStringEnumShape: Boolean
    get() = isEnumShape || isStringShape && hasTrait<@Suppress("DEPRECATION") software.amazon.smithy.model.traits.EnumTrait>()

/**
 * Test if a shape is an error.
 */
val Shape.isError: Boolean
    get() = hasTrait<ErrorTrait>()

/**
 * Test if a shape represents a Kotlin number type
 */
val Shape.isNumberShape: Boolean
    get() = this is NumberShape

/**
 * Test if a shape has the sparse trait applied.
 */
val Shape.isSparse: Boolean
    get() = hasTrait<SparseTrait>()

/**
 * Test if a shape has the streaming trait applied.
 */
val Shape.isStreaming: Boolean
    get() = hasTrait<StreamingTrait>()

/**
 * Test if a member targets an event stream
 */
fun StructureShape.hasEventStreamMember(model: Model): Boolean {
    val streamingMember = findStreamingMember(model) ?: return false
    val target = model.expectShape(streamingMember.target)
    return target.isUnionShape
}

/**
 * Test if an operation input is an event stream
 */
fun OperationShape.isInputEventStream(model: Model): Boolean {
    val reqShape = model.expectShape<StructureShape>(input.get())
    return reqShape.hasEventStreamMember(model)
}

/**
 * Test if an operation output is an event stream
 */
fun OperationShape.isOutputEventStream(model: Model): Boolean {
    val respShape = model.expectShape<StructureShape>(output.get())
    return respShape.hasEventStreamMember(model)
}

/**
 * Test if a structure shape is an operation input
 */
val StructureShape.isOperationInput: Boolean
    get() = hasTrait<OperationInput>()

/**
 * Test if a structure shape is an operation output
 */
val StructureShape.isOperationOutput: Boolean
    get() = hasTrait<OperationOutput>()

/**
 * Return all the members of an event stream union that do not target an error shape.
 * If the current union is not an event stream then it just returns all members
 */
fun UnionShape.filterEventStreamErrors(model: Model): Collection<MemberShape> {
    if (!hasTrait<StreamingTrait>()) return members()

    return members().filterNot {
        val target = model.expectShape(it.target)
        target.isError
    }
}

/**
 * Test if a shape has all optional members (no member marked `@required`)
 */
val Shape.hasAllOptionalMembers: Boolean
    get() = members().none { it.isRequired }

/**
 * Derive the input and output symbols for an operation.
 */
fun OperationIndex.getOperationInputOutputSymbols(op: OperationShape, symbolProvider: SymbolProvider): Pair<Symbol, Symbol> =
    Pair(
        getInput(op).map { symbolProvider.toSymbol(it) }.getOrNull() ?: KotlinTypes.Unit,
        getOutput(op).map { symbolProvider.toSymbol(it) }.getOrNull() ?: KotlinTypes.Unit,
    )

/**
 * Extract a service's endpoint rules if present.
 */
fun ServiceShape.getEndpointRules(): EndpointRuleSet? =
    getTrait<EndpointRuleSetTrait>()?.let {
        EndpointRuleSet.fromNode(it.ruleSet)
    }

/**
 * Extract endpoint test cases from a service if present.
 */
fun ServiceShape.getEndpointTests(): List<EndpointTestCase> =
    getTrait<EndpointTestsTrait>()?.testCases ?: emptyList()
