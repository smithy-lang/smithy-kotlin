/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.model

import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.core.defaultName
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.OperationIndex
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.model.traits.IdempotencyTokenTrait
import software.amazon.smithy.model.traits.StreamingTrait
import software.amazon.smithy.model.traits.Trait
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
 * If is member shape returns target, otherwise returns self.
 * @param model for loading the target shape
 */
internal fun Shape.targetOrSelf(model: Model) = when (this) {
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
fun StructureShape.hasStreamingMember(model: Model): Boolean =
    this.allMembers.values.any { model.getShape(it.target).get().hasTrait<StreamingTrait>() }

/**
 * Returns true if any operation bound to the service contains an input member marked with the IdempotencyTokenTrait
 */
fun ServiceShape.hasIdempotentTokenMember(model: Model) =
    this.operations.any { operationShapeId ->
        val operation = model.expectShape(operationShapeId) as OperationShape
        operation.input.isPresent &&
            model.expectShape(operation.input.get()).members().any { it.hasTrait(IdempotencyTokenTrait.ID.name) }
    }

/**
 * Return the formatted (Kotlin) function signature for the given operation
 */
fun OperationIndex.operationSignature(model: Model, symbolProvider: SymbolProvider, op: OperationShape): String {
    val inputShape = this.getInput(op)
    val outputShape = this.getOutput(op)
    val input = inputShape.map { symbolProvider.toSymbol(it).name }
    val output = outputShape.map { symbolProvider.toSymbol(it).name }

    val hasOutputStream = outputShape.map { it.hasStreamingMember(model) }.orElse(false)
    val inputParam = input.map { "input: $it" }.orElse("")
    val outputParam = output.map { ": $it" }.orElse("")

    val operationName = op.defaultName()

    return if (!hasOutputStream) {
        "suspend fun $operationName($inputParam)$outputParam"
    } else {
        val outputName = output.get()
        val inputSignature = if (inputParam.isNotEmpty()) "$inputParam, " else ""
        "suspend fun <T> $operationName(${inputSignature}block: suspend ($outputName) -> T): T"
    }
}

/**
 * Test if a shape represents an enumeration
 * https://awslabs.github.io/smithy/1.0/spec/core/constraint-traits.html#enum-trait
 */
val Shape.isEnum: Boolean
    get() = isStringShape && hasTrait<EnumTrait>()

/**
 * Test if a shape represents an Kotlin number type
 */
val Shape.isNumberShape: Boolean
    get() = this is NumberShape
