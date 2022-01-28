/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.waiters

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.jmespath.ExpressionVisitor
import software.amazon.smithy.jmespath.JmespathExpression
import software.amazon.smithy.jmespath.RuntimeType
import software.amazon.smithy.jmespath.ast.*
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.utils.dq
import software.amazon.smithy.kotlin.codegen.utils.toCamelCase
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

private val suffixSequence = sequenceOf("") + generateSequence(2) { it + 1 }.map(Int::toString) // "", "2", "3", etc.

/**
 * An [ExpressionVisitor] used for traversing a JMESPath expression to generate code for traversing an equivalent
 * modeled object. This visitor is passed to [JmespathExpression.accept], at which point specific expression methods
 * will be invoked. Code is written immediately to the [KotlinWriter].
 * @param writer The [KotlinWriter] to generate code into.
 */
class KotlinJmespathExpressionVisitor(val writer: KotlinWriter) : ExpressionVisitor<String> {
    private val tempVars = mutableSetOf<String>()

    private fun addTempVar(preferredName: String, codegen: String): String {
        val name = bestTempVarName(preferredName)
        writer.write("val #L = #L", name, codegen)
        return name
    }

    private fun bestTempVarName(preferredName: String): String =
        suffixSequence.map { "$preferredName$it" }.first(tempVars::add)

    private fun childBlock(forExpression: JmespathExpression): String =
        forExpression.accept(KotlinJmespathExpressionVisitor(writer))

    @OptIn(ExperimentalContracts::class)
    private fun codegenReq(condition: Boolean, lazyMessage: () -> String) {
        contract {
            returns() implies condition
        }
        if (!condition) throw CodegenException(lazyMessage())
    }

    private fun flatMappingBlock(right: JmespathExpression, leftName: String): String {
        if (right is CurrentExpression) return leftName // Nothing to map

        val outerName = bestTempVarName("projection")
        writer.openBlock("val #L = #L.flatMap {", outerName, leftName)

        val innerResult = childBlock(right)
        val innerCollector = when (right) {
            is MultiSelectListExpression -> innerResult // Already a list
            else -> "listOfNotNull($innerResult)"
        }
        writer.write(innerCollector)

        writer.closeBlock("}")
        return outerName
    }

    private fun subfield(expression: FieldExpression, parentName: String): String {
        val name = expression.name.toCamelCase()
        return addTempVar(name, "$parentName?.$name")
    }

    override fun visitAnd(expression: AndExpression): String {
        throw CodegenException("AndExpression is unsupported")
    }

    override fun visitComparator(expression: ComparatorExpression): String {
        val left = expression.left!!
        val leftBaseName = left.accept(this)

        val right = expression.right
        val rightBaseName = right.accept(this)

        val leftIsString = (left as? LiteralExpression)?.isStringValue ?: false
        val rightIsString = (right as? LiteralExpression)?.isStringValue ?: false

        val leftName = if (rightIsString && !leftIsString) "$leftBaseName?.toString()" else leftBaseName
        val rightName = if (leftIsString && !rightIsString) "$rightBaseName?.toString()" else rightBaseName

        val codegen = when (val comparator = expression.comparator) {
            ComparatorType.EQUAL, ComparatorType.NOT_EQUAL -> "$leftName $comparator $rightName"
            else -> "if ($leftName == null || $rightName == null) null else $leftName $comparator $rightName"
        }
        return addTempVar("comparison", codegen)
    }

    override fun visitCurrentNode(expression: CurrentExpression): String {
        throw CodegenException("Unexpected current expression outside of flatten expression: $expression")
    }

    override fun visitExpressionType(expression: ExpressionTypeExpression): String {
        throw CodegenException("ExpressionTypeExpression is unsupported")
    }

    override fun visitField(expression: FieldExpression): String = subfield(expression, "it")

    override fun visitFilterProjection(expression: FilterProjectionExpression): String {
        val leftName = expression.left!!.accept(this)
        val filteredName = bestTempVarName("${leftName}Filtered")

        writer.openBlock("val #L = (#L ?: listOf()).filter {", filteredName, leftName)

        val comparisonName = childBlock(expression.comparison!!)
        writer.write("#L == true", comparisonName)

        writer.closeBlock("}")

        val right = expression.right!!
        return flatMappingBlock(right, filteredName)
    }

    override fun visitFlatten(expression: FlattenExpression): String {
        val innerName = expression.expression!!.accept(this)
        return addTempVar("${innerName}OrEmpty", "$innerName ?: listOf()")
    }

    override fun visitFunction(expression: FunctionExpression): String = when (expression.name) {
        "contains" -> {
            codegenReq(expression.arguments.size == 2) { "Unexpected number of arguments to $expression" }

            val subject = expression.arguments[0]
            val subjectName = subject.accept(this)

            val search = expression.arguments[1]
            val searchName = search.accept(this)

            addTempVar("contains", "$subjectName?.contains($searchName) ?: false")
        }

        "length" -> {
            codegenReq(expression.arguments.size == 1) { "Unexpected number of arguments to $expression" }
            writer.addImport(RuntimeTypes.Utils.Convenience.length)

            val subject = expression.arguments[0]
            val subjectName = subject.accept(this)

            addTempVar("length", "$subjectName?.length ?: 0")
        }

        else -> throw CodegenException("Unknown function type in $expression")
    }

    override fun visitIndex(expression: IndexExpression): String {
        throw CodegenException("IndexExpression is unsupported")
    }

    override fun visitLiteral(expression: LiteralExpression): String = when (expression.type) {
        RuntimeType.STRING -> addTempVar("string", expression.expectStringValue().dq())
        RuntimeType.NUMBER -> addTempVar("number", expression.expectNumberValue().toString())
        RuntimeType.BOOLEAN -> addTempVar("bool", expression.expectBooleanValue().toString())
        RuntimeType.NULL -> "null"
        else -> throw CodegenException("Expression type $expression is unsupported")
    }

    override fun visitMultiSelectHash(expression: MultiSelectHashExpression): String {
        throw CodegenException("MultiSelectHashExpression is unsupported")
    }

    override fun visitMultiSelectList(expression: MultiSelectListExpression): String {
        val listName = bestTempVarName("multiSelect")
        writer.openBlock("val #L = listOfNotNull(", listName)

        expression.expressions.forEach { inner ->
            writer.openBlock("run {")
            val innerName = inner.accept(this)
            writer.write(innerName)
            writer.closeBlock("},")
        }

        writer.closeBlock(")")
        return listName
    }

    override fun visitNot(expression: NotExpression): String {
        throw CodegenException("NotExpression is unsupported")
    }

    override fun visitObjectProjection(expression: ObjectProjectionExpression): String {
        val leftName = expression.left!!.accept(this)
        val valuesName = addTempVar("${leftName}Values", "$leftName?.values ?: listOf()")
        return flatMappingBlock(expression.right!!, valuesName)
    }

    override fun visitOr(expression: OrExpression): String {
        throw CodegenException("OrExpression is unsupported")
    }

    override fun visitProjection(expression: ProjectionExpression): String {
        val leftName = expression.left!!.accept(this)
        return flatMappingBlock(expression.right!!, leftName)
    }

    override fun visitSlice(expression: SliceExpression): String {
        throw CodegenException("SliceExpression is unsupported")
    }

    override fun visitSubexpression(expression: Subexpression): String {
        val leftName = expression.left!!.accept(this)

        return when (val right = expression.right!!) {
            is FieldExpression -> subfield(right, leftName)
            else -> throw CodegenException("Subexpression type $right is unsupported")
        }
    }
}
