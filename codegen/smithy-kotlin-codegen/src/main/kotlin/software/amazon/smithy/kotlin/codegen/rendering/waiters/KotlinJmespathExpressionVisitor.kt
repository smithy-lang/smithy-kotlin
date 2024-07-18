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
 * @param topLevelParentName The name used to reference the top level "parent" of an expression during codegen.
 * Defaults to `it`. E.g. `it.field`.
 */
class KotlinJmespathExpressionVisitor(
    val ctx: CodegenContext,
    val writer: KotlinWriter,
    shape: Shape,
    private val topLevelParentName: String = "it",
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

    private fun flatMappingBlock(right: JmespathExpression, leftName: String, leftShape: Shape, innerShape: Shape?): VisitedExpression {
        if (right is CurrentExpression) return VisitedExpression(leftName, leftShape) // nothing to map

        val outerName = bestTempVarName("projection")
        val flatMapExpr = ensureNullGuard(leftShape, "flatMap")
        writer.openBlock("val #L = #L#L {", outerName, leftName, flatMapExpr)

        shapeCursor.addLast(innerShape?.targetMemberOrSelf ?: leftShape.targetMemberOrSelf)
        val innerResult = acceptSubexpression(right)
        shapeCursor.removeLast()

        val innerCollector = when (right) {
            is MultiSelectListExpression, is MultiSelectHashExpression -> innerResult.identifier // Already a list
            else -> "listOfNotNull(${innerResult.identifier})"
        }
        writer.write(innerCollector)

        writer.closeBlock("}")
        return VisitedExpression(outerName, leftShape, innerResult.shape)
    }

    private data class SubFieldData(val name: String, val codegen: String, val member: Shape?)

    private fun subfieldLogic(expression: FieldExpression, parentName: String, isObject: Boolean = false): SubFieldData {
        val member = currentShape.targetOrSelf(ctx.model).getMember(expression.name).getOrNull()

        val name = expression.name.toCamelCase()
        // User created objects are represented as hash maps in code-gen and are marked by `isObject`
        val nameExpr = if (isObject) "[\"$name\"]" else ensureNullGuard(currentShape, name)

        val unwrapExpr = member?.let {
            val memberTarget = ctx.model.expectShape(member.target)
            when {
                memberTarget.isEnum -> "value"
                memberTarget.isEnumList -> "map { it.value }"
                memberTarget.isEnumMap -> "mapValues { (_, v) -> v.value }"
                memberTarget.isBlobShape || memberTarget.isTimestampShape ->
                    throw CodegenException("acceptor behavior for shape type ${memberTarget.type} is undefined")
                else -> null
            }
        }

        val codegen = buildString {
            append("$parentName$nameExpr")
            unwrapExpr?.let { append(ensureNullGuard(member, it)) }
        }

        member?.let { shapeCursor.addLast(it) }
        return SubFieldData(name, codegen, member)
    }

    private fun subfield(expression: FieldExpression, parentName: String, isObject: Boolean = false): VisitedExpression {
        val (name, codegen, member) = subfieldLogic(expression, parentName, isObject)
        return VisitedExpression(addTempVar(name, codegen), member, nullable = currentShape.isNullable)
    }

    private fun subfieldCodegen(expression: FieldExpression, parentName: String, isObject: Boolean = false): String =
        subfieldLogic(expression, parentName, isObject).codegen

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
                if (left.shape?.isNullable == true || left.nullable) add("${left.identifier} == null")
                if (right.shape?.isNullable == true || right.nullable) add("${right.identifier} == null")
            }

            if (nullables.isNotEmpty()) {
                val isNullExpr = nullables.joinToString(" || ")
                append("if ($isNullExpr) null else ")
            }

            val comparatorExpr = ".compareTo(${right.identifier}) ${expression.comparator} 0"
            append("${left.identifier}$comparatorExpr")
        }

        val identifier = addTempVar("comparison", codegen)
        return VisitedExpression(identifier)
    }

    override fun visitCurrentNode(expression: CurrentExpression): VisitedExpression = throw CodegenException("Unexpected current expression outside of flatten expression: $expression")

    override fun visitExpressionType(expression: ExpressionTypeExpression): VisitedExpression = throw CodegenException("ExpressionTypeExpression is unsupported")

    override fun visitField(expression: FieldExpression): VisitedExpression =
        if (shapeCursor.size == 1) subfield(expression, topLevelParentName) else subfield(expression, "it")

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

    private fun FunctionExpression.singleArg(): VisitedExpression =
        acceptSubexpression(this.arguments[0])

    private fun FunctionExpression.twoArgs(): Pair<VisitedExpression, VisitedExpression> =
        acceptSubexpression(this.arguments[0]) to acceptSubexpression(this.arguments[1])

    private fun FunctionExpression.args(): List<VisitedExpression> =
        this.arguments.map { acceptSubexpression(it) }

    private fun VisitedExpression.dotFunction(
        expression: FunctionExpression,
        expr: String,
        elvisExpr: String? = null,
        isObject: Boolean = false,
        ensureNullGuard: Boolean = true,
    ): VisitedExpression {
        val dotFunctionExpr = if (ensureNullGuard) ensureNullGuard(shape, expr, elvisExpr) else ".$expr"
        val ident = addTempVar(expression.name.toCamelCase(), "$identifier$dotFunctionExpr")

        shape?.let { shapeCursor.addLast(shape) }
        return VisitedExpression(ident, shape, isObject = isObject)
    }

    override fun visitFunction(expression: FunctionExpression): VisitedExpression = when (expression.name) {
        "contains" -> {
            val (subject, search) = expression.twoArgs()
            subject.dotFunction(expression, "contains(${search.identifier})", "false")
        }

        "length" -> {
            writer.addImport(RuntimeTypes.Core.Utils.length)
            val subject = expression.singleArg()
            subject.dotFunction(expression, "length", "0")
        }

        "abs", "floor", "ceil" -> {
            val number = expression.singleArg()
            number.dotFunction(expression, "let { kotlin.math.${expression.name}(it.toDouble()) }")
        }

        "sum" -> {
            val numbers = expression.singleArg()
            numbers.dotFunction(expression, "sum()")
        }

        "avg" -> {
            val numbers = expression.singleArg()
            numbers.dotFunction(expression, "average()")
        }

        "join" -> {
            val (glue, list) = expression.twoArgs()
            list.dotFunction(expression, "joinToString(${glue.identifier})")
        }

        "starts_with" -> {
            val (subject, prefix) = expression.twoArgs()
            subject.dotFunction(expression, "startsWith(${prefix.identifier})")
        }

        "ends_with" -> {
            val (subject, suffix) = expression.twoArgs()
            subject.dotFunction(expression, "endsWith(${suffix.identifier})")
        }

        "keys" -> {
            val obj = expression.singleArg()
            VisitedExpression(addTempVar("keys", obj.getKeys()))
        }

        "values" -> {
            val obj = expression.singleArg()
            VisitedExpression(addTempVar("values", obj.getValues()))
        }

        "merge" -> {
            val objects = expression.args()
            VisitedExpression(addTempVar("merge", objects.mergeProperties()), isObject = true)
        }

        "max" -> {
            val list = expression.singleArg()
            list.dotFunction(expression, "maxOrNull()")
        }

        "min" -> {
            val list = expression.singleArg()
            list.dotFunction(expression, "minOrNull()")
        }

        "reverse" -> {
            val listOrString = expression.singleArg()
            listOrString.dotFunction(expression, "reversed()")
        }

        "not_null" -> {
            val args = expression.args()
            VisitedExpression(addTempVar("notNull", args.getNotNull()))
        }

        "to_array" -> {
            val arg = expression.singleArg()
            VisitedExpression(addTempVar("toArray", arg.toArray()))
        }

        "to_string" -> {
            val arg = expression.singleArg()
            VisitedExpression(addTempVar("toString", arg.jmesPathToString()))
        }

        "to_number" -> {
            writer.addImport(RuntimeTypes.Core.Utils.toNumber)
            val arg = expression.singleArg()
            arg.dotFunction(expression, "toNumber()")
        }

        "type" -> {
            writer.addImport(RuntimeTypes.Core.Utils.type)
            val arg = expression.singleArg()
            arg.dotFunction(expression, "type()", ensureNullGuard = false)
        }

        "sort" -> {
            val arg = expression.singleArg()
            arg.dotFunction(expression, "sorted()")
        }

        "sort_by" -> {
            val list = expression.arguments[0].accept(this)
            val expressionValue = expression.arguments[1]
            list.applyFunction(expression.name.toCamelCase(), "sortedBy", expressionValue)
        }

        "max_by" -> {
            val list = expression.arguments[0].accept(this)
            val expressionValue = expression.arguments[1]
            list.applyFunction(expression.name.toCamelCase(), "maxBy", expressionValue)
        }

        "min_by" -> {
            val list = expression.arguments[0].accept(this)
            val expressionValue = expression.arguments[1]
            list.applyFunction(expression.name.toCamelCase(), "minBy", expressionValue)
        }

        "map" -> {
            val list = expression.arguments[1].accept(this)
            val expressionValue = expression.arguments[0]
            list.applyFunction(expression.name.toCamelCase(), "map", expressionValue)
        }

        else -> throw CodegenException("Unknown function type in $expression")
    }

    override fun visitIndex(expression: IndexExpression): VisitedExpression = throw CodegenException("IndexExpression is unsupported")

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
        val properties = expression.expressions.keys.joinToString { "val $it: T" }
        writer.write("class Selection<T>($properties)")

        val listName = bestTempVarName("multiSelect")
        writer.withBlock("val $listName = listOfNotNull(", ")") {
            withBlock("run {", "}") {
                val identifiers = expression.expressions.toList().joinToString { addTempVar(it.first, it.second.accept(this@KotlinJmespathExpressionVisitor).identifier) }
                write("Selection($identifiers)")
            }
        }
        return VisitedExpression(listName, currentShape)
    }

    override fun visitMultiSelectList(expression: MultiSelectListExpression): VisitedExpression {
        val listName = bestTempVarName("multiSelect")
        writer.openBlock("val #L = listOfNotNull(", listName)
        writer.openBlock("listOfNotNull(")

        expression.expressions.forEach {
            writer.openBlock("run {")
            val inner = it.accept(this)
            writer.write(inner.identifier)
            writer.closeBlock("},")
        }

        writer.closeBlock(")")
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
            is FieldExpression -> subfield(expression.left as FieldExpression, parentName)
            is IndexExpression -> index(expression.left as IndexExpression, parentName)
            is Subexpression -> subexpression(expression.left as Subexpression, parentName)
            is ProjectionExpression -> projection(expression.left as ProjectionExpression, parentName)
            else -> expression.left.accept(this)
        }
        requireNotNull(left.shape) { "projection is operating on nothing" }
        return flatMappingBlock(expression.right, left.identifier, left.shape, left.projected)
    }

    override fun visitSlice(expression: SliceExpression): VisitedExpression = throw CodegenException("SliceExpression is unsupported")

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

        val sliceExpr = ensureNullGuard(currentShape, "slice($startIndex..<$stopIndex step ${expression.step}")

        writer.write("@OptIn(ExperimentalStdlibApi::class)")
        val slicedListName = addTempVar("slicedList", "$parentName$sliceExpr)")
        return VisitedExpression(slicedListName, currentShape)
    }

    override fun visitSubexpression(expression: Subexpression): VisitedExpression {
        val left = expression.left.accept(this)
        return processRightSubexpression(expression.right, left.identifier, left.isObject)
    }

    private fun subexpression(expression: Subexpression, parentName: String): VisitedExpression {
        val left = when (val left = expression.left) {
            is FieldExpression -> subfield(left, parentName)
            is Subexpression -> subexpression(left, parentName)
            else -> throw CodegenException("Subexpression type $left is unsupported")
        }
        return processRightSubexpression(expression.right, left.identifier, left.isObject)
    }

    private fun processRightSubexpression(expression: JmespathExpression, leftName: String, isObject: Boolean = false): VisitedExpression =
        when (expression) {
            is FieldExpression -> subfield(expression, leftName, isObject)
            is IndexExpression -> index(expression, leftName)
            is Subexpression -> subexpression(expression, leftName)
            is ProjectionExpression -> projection(expression, leftName)
            else -> throw CodegenException("Subexpression type $expression is unsupported")
        }

    private fun index(expression: IndexExpression, parentName: String): VisitedExpression {
        val index = if (expression.index < 0) "$parentName.size${expression.index}" else expression.index
        val indexExpr = ensureNullGuard(currentShape.targetMemberOrSelf, "get($index)")

        return VisitedExpression(addTempVar("index", "$parentName$indexExpr"), currentShape)
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

    private fun VisitedExpression.getKeys(): String {
        val keys = this.shape?.targetOrSelf(ctx.model)?.allMembers
            ?.keys?.joinToString(", ", "listOf(", ")") { "\"$it\"" }
        return keys ?: "listOf<String>()"
    }

    private fun VisitedExpression.getValues(): String {
        val values = this.shape?.targetOrSelf(ctx.model)?.allMembers?.keys
            ?.joinToString(", ", "listOf(", ")") { "${this.identifier}${ensureNullGuard(this.shape, it)}" }
        return values ?: "listOf<String>()"
    }

    private fun List<VisitedExpression>.mergeProperties(): String {
        val union = addTempVar("union", "HashMap<String, Any?>()")

        forEach { obj ->
            val keys = addTempVar("keys", obj.getKeys())
            val values = addTempVar("values", obj.getValues())

            writer.withBlock("for(i in $keys.indices){", "}") {
                write("union[$keys[i]] = $values[i]")
            }
        }

        return union
    }

    private fun VisitedExpression.jmesPathToString(): String =
        addTempVar("answer", "if(${this.identifier} as Any is String) ${this.identifier} else ${this.identifier}.toString()")

    private fun VisitedExpression.toArray(): String =
        addTempVar("answer", "if(${this.identifier} as Any is List<*> || ${this.identifier} as Any is Array<*>) ${this.identifier} as List<*> else listOf(${this.identifier})")

    private fun List<VisitedExpression>.getNotNull(): String {
        val notNull = bestTempVarName("notNull")

        writer.withBlock("val $notNull = listOfNotNull(", ").firstOrNull()") {
            forEach {
                write("${it.identifier},")
            }
        }

        return notNull
    }

    private fun VisitedExpression.applyFunction(
        name: String,
        operation: String,
        expression: JmespathExpression,
    ): VisitedExpression {
        val result = bestTempVarName(name)

        writer.withBlock("val $result = ${this.identifier}?.$operation {", "}") {
            val expressionValue = subfieldCodegen((expression as ExpressionTypeExpression).expression as FieldExpression, "it")
            write("$expressionValue!!")
        }

        return VisitedExpression(result)
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
 * @param nullable Boolean to indicate that a visited expression is nullable. Shape is used for this mostly but sometimes an
 *                 expression is nullable for reasons that are not shape related
 * @param isObject Boolean to indicate that a visited expression results in an object. Objects are represented as hash maps
 *                 because it is not possible to construct a class at runtime
 */
data class VisitedExpression(val identifier: String, val shape: Shape? = null, val projected: Shape? = null, val nullable: Boolean = false, val isObject: Boolean = false)
