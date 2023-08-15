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
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.model.isEnum
import software.amazon.smithy.kotlin.codegen.model.targetOrSelf
import software.amazon.smithy.kotlin.codegen.model.traits.OperationInput
import software.amazon.smithy.kotlin.codegen.model.traits.OperationOutput
import software.amazon.smithy.kotlin.codegen.utils.dq
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.kotlin.codegen.utils.toCamelCase
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

    // tracks the current shape on which the visitor is operating
    private val shapeCursor = ArrayDeque(listOf(shape))

    private val currentShape: Shape
        get() = shapeCursor.last()

    // traverses an independent expression (one whose resolved scope does not persist in the outer evaluation)
    private fun acceptSubexpression(expr: JmespathExpression): VisitedExpression {
        val pos = shapeCursor.size
        val out = expr.accept(this)

        val diff = shapeCursor.size - pos
        repeat(diff) { shapeCursor.removeLast() } // reset the shape cursor

        return out
    }

    private fun addTempVar(preferredName: String, codegen: String): String {
        val name = bestTempVarName(preferredName)
        writer.write("val #L = #L", name, codegen)
        return name
    }

    private fun bestTempVarName(preferredName: String): String =
        suffixSequence.map { "$preferredName$it" }.first(tempVars::add)

    @OptIn(ExperimentalContracts::class)
    private fun codegenReq(condition: Boolean, lazyMessage: () -> String) {
        contract {
            returns() implies condition
        }
        if (!condition) throw CodegenException(lazyMessage())
    }

    private fun flatMappingBlock(right: JmespathExpression, leftName: String, leftShape: Shape, innerShape: Shape?): VisitedExpression {
        if (right is CurrentExpression) return VisitedExpression(leftName, leftShape) // nothing to map

        val outerName = bestTempVarName("projection")
        val flatMapExpr = ensureNullGuard(leftShape, "flatMap")
        writer.openBlock("val #L = #L#L {", outerName, leftName, flatMapExpr)

        shapeCursor.addLast(innerShape?.targetMemberOrSelf ?: leftShape.targetMemberOrSelf)
        val innerResult = acceptSubexpression(right)
        shapeCursor.removeLast()

        val innerCollector = when (right) {
            is MultiSelectListExpression -> innerResult.identifier // Already a list
            else -> "listOfNotNull(${innerResult.identifier})"
        }
        writer.write(innerCollector)

        writer.closeBlock("}")
        return VisitedExpression(outerName, leftShape, innerResult.shape)
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
                if (right.shape?.isNullable == true) add("${right.identifier} == null")
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
        val left = expression.left.accept(this)
        requireNotNull(left.shape) { "filter projection is operating on nothing?" }

        val filteredName = bestTempVarName("${left.identifier}Filtered")

        val filterExpr = ensureNullGuard(left.shape, "filter")
        writer.withBlock("val #L = #L#L {", "}", filteredName, left.identifier, filterExpr) {
            shapeCursor.addLast(left.shape.targetMemberOrSelf)
            val comparison = acceptSubexpression(expression.comparison)
            shapeCursor.removeLast()
            write("#L == true", comparison.identifier)
        }

        return flatMappingBlock(expression.right, filteredName, left.shape, left.projected)
    }

    override fun visitFlatten(expression: FlattenExpression): VisitedExpression {
        writer.addImport(RuntimeTypes.Core.Utils.flattenIfPossible)

        val inner = expression.expression.accept(this)

        val flattenExpr = ensureNullGuard(currentShape, "flattenIfPossible()")
        val ident = addTempVar("${inner.identifier}OrEmpty", "${inner.identifier}$flattenExpr")

        return VisitedExpression(ident, currentShape, inner.projected)
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

        return flatMappingBlock(expression.right, valuesName, left.shape, left.projected)
    }

    override fun visitOr(expression: OrExpression): VisitedExpression {
        writer.addImport(RuntimeTypes.Core.Utils.truthiness)

        val left = acceptSubexpression(expression.left)
        val leftTruthinessName = addTempVar("${left.identifier}Truthiness", "truthiness(${left.identifier})")

        val right = acceptSubexpression(expression.right)

        val ident = addTempVar("or", "if ($leftTruthinessName) ${left.identifier} else ${right.identifier}")
        return VisitedExpression(ident)
    }

    override fun visitProjection(expression: ProjectionExpression): VisitedExpression {
        val left = expression.left.accept(this)
        requireNotNull(left.shape) { "projection is operating on nothing?" }

        return flatMappingBlock(expression.right, left.identifier, left.shape, left.projected)
    }

    private fun projection(expression: ProjectionExpression, parentName: String): VisitedExpression {
        val left = when (expression.left) {
            is SliceExpression -> slice(expression.left as SliceExpression, parentName)
            else -> expression.left.accept(this)
        }
        requireNotNull(left.shape) { "projection is operating on nothing" }
        return flatMappingBlock(expression.right, left.identifier, left.shape, left.projected)
    }

    override fun visitSlice(expression: SliceExpression): VisitedExpression {
        throw CodegenException("SliceExpression is unsupported")
    }

    private fun slice(expression: SliceExpression, parentName: String): VisitedExpression {
        val startIndex = if (!expression.start.isPresent) {
            "0"
        } else {
            if (expression.start.asInt < 0) "$parentName.size${expression.start.asInt}" else expression.start.asInt
        }

        val stopIndex = if (!expression.stop.isPresent) {
            "$parentName.size"
        } else {
            if (expression.stop.asInt < 0) "$parentName.size${expression.stop.asInt}" else expression.stop.asInt
        }

        writer.write("@OptIn(ExperimentalStdlibApi::class)")
        val slicedListName = addTempVar("slicedList", "$parentName?.slice($startIndex..<$stopIndex step ${expression.step})")
        return VisitedExpression(slicedListName, currentShape)
    }

    override fun visitSubexpression(expression: Subexpression): VisitedExpression {
        val leftName = expression.left.accept(this).identifier
        return processRightSubexpression(expression.right, leftName)
    }

    private fun subexpression(expression: Subexpression, parentName: String): VisitedExpression {
        val leftName = when (val left = expression.left) {
            is FieldExpression -> subfield(left, parentName).identifier
            is Subexpression -> subexpression(left, parentName).identifier
            else -> throw CodegenException("Subexpression type $left is unsupported")
        }
        return processRightSubexpression(expression.right, leftName)
    }

    private fun processRightSubexpression(expression: JmespathExpression, leftName: String): VisitedExpression =
        when (expression) {
            is FieldExpression -> subfield(expression, leftName)
            is IndexExpression -> index(expression, leftName)
            is Subexpression -> subexpression(expression, leftName)
            is ProjectionExpression -> projection(expression, leftName)
            else -> throw CodegenException("Subexpression type $expression is unsupported")
        }

    private fun index(expression: IndexExpression, parentName: String): VisitedExpression {
        shapeCursor.addLast(currentShape.targetOrSelf(ctx.model).targetMemberOrSelf)
        val index = if (expression.index < 0) "$parentName.size${expression.index}" else expression.index
        return VisitedExpression(addTempVar("index", "$parentName?.get($index)"))
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
            ctx.model.expectShape(target).let { !it.hasTrait<OperationInput>() && !it.hasTrait<OperationOutput>() }

    private val Shape.targetMemberOrSelf: Shape
        get() = when (val target = targetOrSelf(ctx.model)) {
            is ListShape -> target.member
            is MapShape -> target.value
            else -> this
        }
}

/**
 * Contains information about the output of a visited [JmespathExpression].
 * @param identifier The generated identifier in which the expression result is stored.
 * @param shape The underlying shape (if any) that the identifier represents. Not all expressions reference a modeled
 *              shape, e.g. [LiteralExpression] (the value is just a literal) or [FunctionExpression]s where the
 *              returned value is scalar.
 * @param projected For projections, the context of the inner shape. For example, given the expression
 *                  `foo[].bar[].baz.qux`, the shape that backs the identifier (and therefore determines overall nullability)
 *                  is `foo`, but the shape that needs carried through to subfield expressions in the following projection
 *                  is the target of `bar`, such that its subfields `baz` and `qux` can be recognized.
 */
data class VisitedExpression(val identifier: String, val shape: Shape? = null, val projected: Shape? = null)
