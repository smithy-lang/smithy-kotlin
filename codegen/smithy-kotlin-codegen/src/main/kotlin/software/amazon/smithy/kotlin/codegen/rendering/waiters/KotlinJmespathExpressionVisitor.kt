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
 * Each step of the traversal returns a [VisitedExpression]. Any intermediate code required to express the query is
 * written immediately to the provided writer.
 *
 * @param ctx The surrounding [CodegenContext].
 * @param writer The [KotlinWriter] to generate code into.
 * @param shape The modeled [Shape] on which this JMESPath expression is operating.
 */
class KotlinJmespathExpressionVisitor(
    val ctx: CodegenContext,
    val writer: KotlinWriter,
    shape: Shape,
) : ExpressionVisitor<VisitedExpression> {
    private val tempVars = mutableSetOf<String>()

    private val nullableIndex = NullableIndex(ctx.model)

    // tracks the current shape on which the visitor is operating
    private val shapeCursor = ArrayDeque(listOf(shape))

    private val currentShape: Shape
        get() = shapeCursor.last()

    private fun acceptSubexpression(expr: JmespathExpression): VisitedExpression {
        shapeCursor.addLast(currentShape)
        val out = expr.accept(this)
        shapeCursor.removeLast()
        return out
    }

    private fun addTempVar(preferredName: String, codegen: String): String {
        val name = bestTempVarName(preferredName)
        writer.write("val #L = #L", name, codegen)
        return name
    }

    private fun bestTempVarName(preferredName: String): String =
        suffixSequence.map { "$preferredName$it" }.first(tempVars::add)

    private fun childBlock(forExpression: JmespathExpression, shape: Shape): VisitedExpression {
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

    private fun flatMappingBlock(right: JmespathExpression, leftName: String, leftShape: Shape): VisitedExpression {
        if (right is CurrentExpression) return VisitedExpression(leftName, leftShape) // nothing to map

        val outerName = bestTempVarName("projection")
        writer.openBlock("val #L = #L.flatMap {", outerName, leftName)

        val innerResult = childBlock(right, leftShape)
        val innerCollector = when (right) {
            is MultiSelectListExpression -> innerResult.identifier // Already a list
            else -> "listOfNotNull(${innerResult.identifier})"
        }
        writer.write(innerCollector)

        writer.closeBlock("}")
        return VisitedExpression(outerName, innerResult.shape)
    }

    private fun subfield(expression: FieldExpression, parentName: String): VisitedExpression {
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

        shapeCursor.addLast(member)
        return VisitedExpression(addTempVar(name, codegen), member)
    }

    override fun visitAnd(expression: AndExpression): VisitedExpression {
        writer.addImport(RuntimeTypes.Core.Utils.truthiness)

        val left = acceptSubexpression(expression.left)
        val leftTruthinessName = addTempVar("${left.identifier}Truthiness", "truthiness(${left.identifier})")

        val right = acceptSubexpression(expression.right)

        val ident = addTempVar("and", "if ($leftTruthinessName) ${right.identifier} else ${left.identifier}")
        return VisitedExpression(ident)
    }

    override fun visitComparator(expression: ComparatorExpression): VisitedExpression {
        val left = acceptSubexpression(expression.left)
        val right = acceptSubexpression(expression.right)

        val codegen = buildString {
            val nullables = buildList {
                if (left.shape?.isNullable == true) add("${left.identifier} == null")
                if (right.shape?.isNullable == true) add("${left.identifier} == null")
            }
            if (nullables.isNotEmpty()) {
                val isNullExpr = nullables.joinToString(" || ")
                append("if ($isNullExpr) null else ")
            }

            append("${left.identifier}.compareTo(${right.identifier}) ${expression.comparator} 0")
        }

        val ident = addTempVar("comparison", codegen)
        return VisitedExpression(ident)
    }

    override fun visitCurrentNode(expression: CurrentExpression): VisitedExpression {
        throw CodegenException("Unexpected current expression outside of flatten expression: $expression")
    }

    override fun visitExpressionType(expression: ExpressionTypeExpression): VisitedExpression {
        throw CodegenException("ExpressionTypeExpression is unsupported")
    }

    override fun visitField(expression: FieldExpression): VisitedExpression = subfield(expression, "it")

    override fun visitFilterProjection(expression: FilterProjectionExpression): VisitedExpression {
        val left = acceptSubexpression(expression.left)
        requireNotNull(left.shape) { "filter projection is operating on nothing?" }

        val filteredName = bestTempVarName("${left.identifier}Filtered")

        val filterExpr = ensureNullGuard(left.shape, "filter")
        writer.withBlock("val #L = #L#L {", "}", filteredName, left.identifier, filterExpr) {
            val comparison = childBlock(expression.comparison, left.shape)
            write("#L == true", comparison.identifier)
        }

        return flatMappingBlock(expression.right, filteredName, left.shape)
    }

    override fun visitFlatten(expression: FlattenExpression): VisitedExpression {
        writer.addImport(RuntimeTypes.Core.Utils.flattenIfPossible)

        val inner = acceptSubexpression(expression.expression)
        val flattenExpr = ensureNullGuard(inner.shape, "flattenIfPossible()", "listOf()")
        val ident = addTempVar("${inner.identifier}OrEmpty", "${inner.identifier}$flattenExpr")
        return VisitedExpression(ident, inner.shape)
    }

    override fun visitFunction(expression: FunctionExpression): VisitedExpression = when (expression.name) {
        "contains" -> {
            codegenReq(expression.arguments.size == 2) { "Unexpected number of arguments to $expression" }

            val subject = acceptSubexpression(expression.arguments[0])
            val search = acceptSubexpression(expression.arguments[1])

            val containsExpr = ensureNullGuard(subject.shape, "contains(${search.identifier})", "false")
            val ident = addTempVar("contains", "${subject.identifier}$containsExpr")
            VisitedExpression(ident)
        }

        "length" -> {
            codegenReq(expression.arguments.size == 1) { "Unexpected number of arguments to $expression" }
            writer.addImport(RuntimeTypes.Core.Utils.length)

            val subject = acceptSubexpression(expression.arguments[0])

            val lengthExpr = ensureNullGuard(subject.shape, "length", "0")
            val ident = addTempVar("length", "${subject.identifier}$lengthExpr")
            VisitedExpression(ident)
        }

        else -> throw CodegenException("Unknown function type in $expression")
    }

    override fun visitIndex(expression: IndexExpression): VisitedExpression {
        throw CodegenException("IndexExpression is unsupported")
    }

    override fun visitLiteral(expression: LiteralExpression): VisitedExpression {
        val ident = when (expression.type) {
            RuntimeType.STRING -> addTempVar("string", expression.expectStringValue().dq())
            RuntimeType.NUMBER -> addTempVar("number", expression.expectNumberValue().toString())
            RuntimeType.BOOLEAN -> addTempVar("bool", expression.expectBooleanValue().toString())
            RuntimeType.NULL -> "null"
            else -> throw CodegenException("Expression type $expression is unsupported")
        }

        return VisitedExpression(ident)
    }

    override fun visitMultiSelectHash(expression: MultiSelectHashExpression): VisitedExpression {
        throw CodegenException("MultiSelectHashExpression is unsupported")
    }

    override fun visitMultiSelectList(expression: MultiSelectListExpression): VisitedExpression {
        val listName = bestTempVarName("multiSelect")
        writer.openBlock("val #L = listOfNotNull(", listName)

        expression.expressions.forEach {
            writer.openBlock("run {")
            val inner = acceptSubexpression(it)
            writer.write(inner.identifier)
            writer.closeBlock("},")
        }

        writer.closeBlock(")")
        return VisitedExpression(listName, currentShape)
    }

    override fun visitNot(expression: NotExpression): VisitedExpression {
        writer.addImport(RuntimeTypes.Core.Utils.truthiness)

        val operand = acceptSubexpression(expression.expression)
        val truthinessName = addTempVar("${operand.identifier}Truthiness", "truthiness(${operand.identifier})")
        val notName = "not${operand.identifier.replaceFirstChar(Char::uppercaseChar)}"

        val ident = addTempVar(notName, "!$truthinessName")
        return VisitedExpression(ident)
    }

    override fun visitObjectProjection(expression: ObjectProjectionExpression): VisitedExpression {
        val left = acceptSubexpression(expression.left)
        requireNotNull(left.shape) { "object projection is operating on nothing?" }

        val valuesExpr = ensureNullGuard(left.shape, "values")
        val valuesName = addTempVar("${left.identifier}Values", "${left.identifier}$valuesExpr")

        return flatMappingBlock(expression.right, valuesName, left.shape)
    }

    override fun visitOr(expression: OrExpression): VisitedExpression {
        writer.addImport(RuntimeTypes.Core.Utils.truthiness)

        val left = acceptSubexpression(expression.left)
        val leftTruthinessName = addTempVar("${left.identifier}Truthiness", "truthiness($${left.identifier})")

        val right = acceptSubexpression(expression.right)

        val ident = addTempVar("or", "if ($leftTruthinessName) ${left.identifier} else ${right.identifier}")
        return VisitedExpression(ident)
    }

    override fun visitProjection(expression: ProjectionExpression): VisitedExpression {
        val left = acceptSubexpression(expression.left)
        requireNotNull(left.shape) { "projection is operating on nothing?" }

        return flatMappingBlock(expression.right, left.identifier, left.shape)
    }

    override fun visitSlice(expression: SliceExpression): VisitedExpression {
        throw CodegenException("SliceExpression is unsupported")
    }

    override fun visitSubexpression(expression: Subexpression): VisitedExpression {
        val left = acceptSubexpression(expression.left)
        requireNotNull(left.shape)

        shapeCursor.addLast(left.shape)
        val ret = when (val right = expression.right) {
            is FieldExpression -> subfield(right, left.identifier)
            else -> throw CodegenException("Subexpression type $right is unsupported")
        }
        shapeCursor.removeLast()

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

/**
 * Contains information about the output of a visited [JmespathExpression].
 * @param identifier The generated identifier in which the expression result is stored.
 * @param shape The underlying shape (if any) that the identifier represents. Not all expressions reference a modeled
 *              shape, e.g. [LiteralExpression] (the value is just a literal) or [FunctionExpression]s where the
 *              returned value is scalar.
 */
data class VisitedExpression(val identifier: String, val shape: Shape? = null)
