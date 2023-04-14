/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.waiters

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.jmespath.ExpressionVisitor
import software.amazon.smithy.jmespath.JmespathExpression
import software.amazon.smithy.jmespath.RuntimeType
import software.amazon.smithy.jmespath.ast.*
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.model.*
import software.amazon.smithy.kotlin.codegen.model.traits.OperationInput
import software.amazon.smithy.kotlin.codegen.model.traits.OperationOutput
import software.amazon.smithy.kotlin.codegen.utils.dq
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.kotlin.codegen.utils.toCamelCase
import software.amazon.smithy.model.knowledge.NullableIndex
import software.amazon.smithy.model.shapes.ListShape
import software.amazon.smithy.model.shapes.MapShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

private val suffixSequence = sequenceOf("") + generateSequence(2) { it + 1 }.map(Int::toString) // "", "2", "3", etc.

/**
 * An [ExpressionVisitor] used for traversing a JMESPath expression to generate code for traversing an equivalent
 * modeled object. This visitor is passed to [JmespathExpression.accept], at which point specific expression methods
 * will be invoked.
 *
 * Each step of the traversal returns a tuple of the following:
 * - A string representing the generated identifier in which the query result is stored.
 * - The underlying shape (if any) that the identifier represents. Not all expressions reference a modeled shape, e.g.
 *   [LiteralExpression] (the value is just a literal) or [FunctionExpression]s where the returned value is scalar.
 * Any intermediate code required to express the query is written immediately to the provided writer.
 *
 * @param ctx The surrounding [CodegenContext].
 * @param writer The [KotlinWriter] to generate code into.
 * @param shape The modeled [Shape] on which this JMESPath expression is operating.
 */
class KotlinJmespathExpressionVisitor(
    val ctx: CodegenContext,
    val writer: KotlinWriter,
    shape: Shape,
) : ExpressionVisitor<Pair<String, Shape?>> {
    private val tempVars = mutableSetOf<String>()

    private val nullableIndex = NullableIndex(ctx.model)

    private var currentShape: Shape = shape

    // preserve currentShape state as we traverse subexpressions
    private var currentShapeStack = ArrayDeque<Shape>()

    private fun acceptSubexpression(expr: JmespathExpression): Pair<String, Shape?> {
        currentShapeStack.addLast(currentShape)
        val out = expr.accept(this)
        currentShape = currentShapeStack.removeLast()
        return out
    }

    private fun addTempVar(preferredName: String, codegen: String): String {
        val name = bestTempVarName(preferredName)
        writer.write("val #L = #L", name, codegen)
        return name
    }

    private fun bestTempVarName(preferredName: String): String =
        suffixSequence.map { "$preferredName$it" }.first(tempVars::add)

    private fun childBlock(forExpression: JmespathExpression, shape: Shape): Pair<String, Shape?> {
        val childShape = when (val target = shape.targetOrSelf(ctx.model)) {
            is ListShape -> target.member
            is MapShape -> target.value
            else -> shape
        }
        return forExpression.accept(KotlinJmespathExpressionVisitor(ctx, writer, childShape))
    }

    @OptIn(ExperimentalContracts::class)
    private fun codegenReq(condition: Boolean, lazyMessage: () -> String) {
        contract {
            returns() implies condition
        }
        if (!condition) throw CodegenException(lazyMessage())
    }

    private fun flatMappingBlock(right: JmespathExpression, leftName: String, leftShape: Shape): String {
        if (right is CurrentExpression) return leftName // Nothing to map

        val outerName = bestTempVarName("projection")
        writer.openBlock("val #L = #L.flatMap {", outerName, leftName)

        val (innerResult, _) = childBlock(right, leftShape)
        val innerCollector = when (right) {
            is MultiSelectListExpression -> innerResult // Already a list
            else -> "listOfNotNull($innerResult)"
        }
        writer.write(innerCollector)

        writer.closeBlock("}")
        return outerName
    }

    private fun subfield(expression: FieldExpression, parentName: String): Pair<String, Shape?> {
        val member = currentShape.targetOrSelf(ctx.model).getMember(expression.name).getOrNull()
            ?: throw CodegenException("reference to nonexistent member '${expression.name}' of shape $currentShape")

        val name = expression.name.toCamelCase()
        val nameExpr = ensureNullGuard(currentShape, name)

        val memberTarget = ctx.model.expectShape(member.target)
        val unwrapExpr = when {
            memberTarget.isEnum -> "value"
            memberTarget.isEnumList -> "map { it.value }"
            memberTarget.isEnumMap -> "mapValues { (_, v) -> v.value }"
            memberTarget.isBlobShape || memberTarget.isTimestampShape ->
                throw CodegenException("acceptor behavior for shape type ${memberTarget.type} is undefined")
            else -> null
        }
        val codegen = buildString {
            append("$parentName$nameExpr")
            unwrapExpr?.let { append(ensureNullGuard(member, it)) }
        }

        currentShape = member
        return addTempVar(name, codegen) to currentShape
    }

    override fun visitAnd(expression: AndExpression): Pair<String, Shape?> {
        writer.addImport(RuntimeTypes.Core.Utils.truthiness)

        val (leftName, _) = acceptSubexpression(expression.left)
        val leftTruthinessName = addTempVar("${leftName}Truthiness", "truthiness($leftName)")

        val (rightName, _) = acceptSubexpression(expression.right)

        return addTempVar("and", "if ($leftTruthinessName) $rightName else $leftName") to null
    }

    override fun visitComparator(expression: ComparatorExpression): Pair<String, Shape?> {
        val (leftName, leftShape) = acceptSubexpression(expression.left)
        val (rightName, rightShape) = acceptSubexpression(expression.right)

        val codegen = buildString {
            val nullables = buildList {
                if (leftShape?.isNullable == true) add("$leftName == null")
                if (rightShape?.isNullable == true) add("$rightName == null")
            }
            if (nullables.isNotEmpty()) {
                val isNullExpr = nullables.joinToString(" || ")
                append("if ($isNullExpr) null else ")
            }

            append("$leftName.compareTo($rightName) ${expression.comparator} 0")
        }

        return addTempVar("comparison", codegen) to null
    }

    override fun visitCurrentNode(expression: CurrentExpression): Pair<String, Shape?> {
        throw CodegenException("Unexpected current expression outside of flatten expression: $expression")
    }

    override fun visitExpressionType(expression: ExpressionTypeExpression): Pair<String, Shape?> {
        throw CodegenException("ExpressionTypeExpression is unsupported")
    }

    override fun visitField(expression: FieldExpression): Pair<String, Shape?> = subfield(expression, "it")

    override fun visitFilterProjection(expression: FilterProjectionExpression): Pair<String, Shape?> {
        val (leftName, leftShape) = acceptSubexpression(expression.left)
        requireNotNull(leftShape) { "filter projection is operating on nothing?" }

        val filteredName = bestTempVarName("${leftName}Filtered")

        val filterExpr = ensureNullGuard(leftShape, "filter")
        writer.withBlock("val #L = #L#L {", "}", filteredName, leftName, filterExpr) {
            val (comparisonName, _) = childBlock(expression.comparison, leftShape)
            write("#L == true", comparisonName)
        }

        return flatMappingBlock(expression.right, filteredName, leftShape) to leftShape
    }

    override fun visitFlatten(expression: FlattenExpression): Pair<String, Shape?> {
        writer.addImport(RuntimeTypes.Core.Utils.flattenIfPossible)

        val (innerName, innerShape) = acceptSubexpression(expression.expression)
        val flattenExpr = ensureNullGuard(innerShape, "flattenIfPossible()", "listOf()")
        return addTempVar("${innerName}OrEmpty", "$innerName$flattenExpr") to innerShape
    }

    override fun visitFunction(expression: FunctionExpression): Pair<String, Shape?> = when (expression.name) {
        "contains" -> {
            codegenReq(expression.arguments.size == 2) { "Unexpected number of arguments to $expression" }

            val subject = expression.arguments[0]
            val (subjectName, subjectShape) = acceptSubexpression(subject)

            val search = expression.arguments[1]
            val (searchName, _) = acceptSubexpression(search)

            val containsExpr = ensureNullGuard(subjectShape, "contains($searchName)", "false")
            addTempVar("contains", "$subjectName$containsExpr") to null
        }

        "length" -> {
            codegenReq(expression.arguments.size == 1) { "Unexpected number of arguments to $expression" }
            writer.addImport(RuntimeTypes.Core.Utils.length)

            val subject = expression.arguments[0]
            val (subjectName, subjectShape) = acceptSubexpression(subject)

            val lengthExpr = ensureNullGuard(subjectShape, "length", "0")
            addTempVar("length", "$subjectName$lengthExpr") to null
        }

        else -> throw CodegenException("Unknown function type in $expression")
    }

    override fun visitIndex(expression: IndexExpression): Pair<String, Shape?> {
        throw CodegenException("IndexExpression is unsupported")
    }

    override fun visitLiteral(expression: LiteralExpression): Pair<String, Shape?> = when (expression.type) {
        RuntimeType.STRING -> addTempVar("string", expression.expectStringValue().dq()) to null
        RuntimeType.NUMBER -> addTempVar("number", expression.expectNumberValue().toString()) to null
        RuntimeType.BOOLEAN -> addTempVar("bool", expression.expectBooleanValue().toString()) to null
        RuntimeType.NULL -> "null" to null
        else -> throw CodegenException("Expression type $expression is unsupported")
    }

    override fun visitMultiSelectHash(expression: MultiSelectHashExpression): Pair<String, Shape?> {
        throw CodegenException("MultiSelectHashExpression is unsupported")
    }

    override fun visitMultiSelectList(expression: MultiSelectListExpression): Pair<String, Shape?> {
        val listName = bestTempVarName("multiSelect")
        writer.openBlock("val #L = listOfNotNull(", listName)

        expression.expressions.forEach { inner ->
            writer.openBlock("run {")
            val (innerName, _) = acceptSubexpression(inner)
            writer.write(innerName)
            writer.closeBlock("},")
        }

        writer.closeBlock(")")
        return listName to currentShape
    }

    override fun visitNot(expression: NotExpression): Pair<String, Shape?> {
        writer.addImport(RuntimeTypes.Core.Utils.truthiness)

        val (operandName, _) = acceptSubexpression(expression.expression)
        val truthinessName = addTempVar("${operandName}Truthiness", "truthiness($operandName)")
        val notName = "not${operandName.replaceFirstChar(Char::uppercaseChar)}"
        return addTempVar(notName, "!$truthinessName") to null
    }

    override fun visitObjectProjection(expression: ObjectProjectionExpression): Pair<String, Shape?> {
        val (left, leftShape) = acceptSubexpression(expression.left)
        requireNotNull(leftShape) { "object projection is operating on nothing?" }

        val valuesExpr = ensureNullGuard(leftShape, "values")
        val valuesName = addTempVar("${left}Values", "$left$valuesExpr")
        return flatMappingBlock(expression.right, valuesName, leftShape) to leftShape
    }

    override fun visitOr(expression: OrExpression): Pair<String, Shape?> {
        writer.addImport(RuntimeTypes.Core.Utils.truthiness)

        val (leftName, _) = acceptSubexpression(expression.left)
        val leftTruthinessName = addTempVar("${leftName}Truthiness", "truthiness($leftName)")

        val (rightName, _) = acceptSubexpression(expression.right)

        return addTempVar("or", "if ($leftTruthinessName) $leftName else $rightName") to null
    }

    override fun visitProjection(expression: ProjectionExpression): Pair<String, Shape?> {
        val (leftName, leftShape) = acceptSubexpression(expression.left)
        requireNotNull(leftShape) { "projection is operating on nothing?" }

        return flatMappingBlock(expression.right, leftName, requireNotNull(leftShape)) to leftShape
    }

    override fun visitSlice(expression: SliceExpression): Pair<String, Shape?> {
        throw CodegenException("SliceExpression is unsupported")
    }

    override fun visitSubexpression(expression: Subexpression): Pair<String, Shape?> {
        val (leftName, leftShape) = acceptSubexpression(expression.left)
        requireNotNull(leftShape)

        currentShapeStack.addLast(currentShape)
        currentShape = leftShape
        val ret = when (val right = expression.right) {
            is FieldExpression -> subfield(right, leftName)
            else -> throw CodegenException("Subexpression type $right is unsupported")
        }
        currentShape = currentShapeStack.removeLast()

        return ret
    }

    private val Shape.isEnumList: Boolean
        get() = this is ListShape && ctx.model.expectShape(member.target).isEnum

    private val Shape.isEnumMap: Boolean
        get() = this is MapShape && ctx.model.expectShape(value.target).isEnum

    private fun ensureNullGuard(shape: Shape?, expr: String, elvisExpr: String? = null): String =
        if (shape?.isNullable == true) {
            buildString {
                append("?.$expr")
                elvisExpr?.let { append(" ?: $it") }
            }
        } else {
            ".$expr"
        }

    private val Shape.isNullable: Boolean
        get() = this is MemberShape &&
                ctx.model.expectShape(target).let { !it.hasTrait<OperationInput>() && !it.hasTrait<OperationOutput>() } &&
                nullableIndex.isMemberNullable(this, NullableIndex.CheckMode.CLIENT_ZERO_VALUE_V1_NO_INPUT)
}
