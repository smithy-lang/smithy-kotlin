/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.waiters

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.jmespath.ExpressionVisitor
import software.amazon.smithy.jmespath.JmespathExpression
import software.amazon.smithy.jmespath.ast.*
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.model.isBoxed
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.StructureShape

/**
 * An [ExpressionVisitor] used for traversing a JMESPath expression to generate code for traversing an equivalent
 * modeled object.
 * @param includeInput Determines if this expression utilizes output only (false) or both input and output (true). If
 * false, the top-level object containing the output will be `it`. If true, the top-level object `it` will contain
 * `input` and `output` members with the request and response respectively.
 * @param model The [Model] to use for code generation.
 * @param inputShape The [StructureShape] of the input to the operation. Unused if [includeInput] is false.
 * @param inputSymbol The [Symbol] of the input to the operation. Unused if [includeInput] is false.
 * @param outputShape The [StructureShape] of the output from the operation.
 * @param outputSymbol The [Symbol] of the output from the operation.
 */
class KotlinJmespathExpressionVisitor(
    val includeInput: Boolean,
    val model: Model,
    val symbolProvider: SymbolProvider,
    val inputShape: StructureShape,
    val inputSymbol: Symbol,
    val outputShape: StructureShape,
    val outputSymbol: Symbol,
) : ExpressionVisitor<Unit> {
    private val tempVars = mutableMapOf<JmespathExpression, TempVar>()

    private fun addTempVar(
        jmespathExpression: JmespathExpression,
        preferredName: String,
        codegenExpression: String,
        shape: Shape,
        symbol: Symbol,
    ) {
        fun nameAvailable(name: String) = tempVars.values.none { it.name == name }

        val name = when {
            nameAvailable(preferredName) -> preferredName
            else -> generateSequence(1) { it + 1 }.map { "$preferredName$it" }.first(::nameAvailable)
        }

        tempVars[jmespathExpression] = TempVar(name, codegenExpression, shape, symbol)
    }

    /**
     * Renders the actual code for a path-based matcher by outputting a series of variable assignments for the
     * expression.
     * @param writer The [KotlinWriter] to use for code generation.
     * @return The name of the variable containing the result of the expression.
     */
    fun renderActual(writer: KotlinWriter): String {
        tempVars.values.forEach { writer.write("val #L = #L", it.name, it.codegenValue) }
        return tempVars.values.last().name
    }

    override fun visitAnd(expression: AndExpression) {
        println("Visited $expression")
        TODO("Not yet implemented")
    }

    override fun visitComparator(expression: ComparatorExpression) {
        println("Visited $expression")
        TODO("Not yet implemented")
    }

    override fun visitCurrentNode(expression: CurrentExpression) {
        println("Visited $expression")
        TODO("Not yet implemented")
    }

    override fun visitExpressionType(expression: ExpressionTypeExpression) {
        println("Visited $expression")
        TODO("Not yet implemented")
    }

    override fun visitField(expression: FieldExpression) {
        val name = expression.name
        if (includeInput) {
            when (name) {
                "input" -> addTempVar(expression, "input", "it.input", inputShape, inputSymbol)
                "output" -> addTempVar(expression, "output", "it.output", outputShape, outputSymbol)
                else -> TODO("Don't know how to handle root field $name")
            }
        } else {
            val memberShape = outputShape.expectMember(name)
            val memberName = symbolProvider.toMemberName(memberShape)
            val memberSymbol = symbolProvider.toSymbol(memberShape)
            val targetShape = model.expectShape(memberShape.target)
            addTempVar(expression, memberName, "it.$memberName", targetShape, memberSymbol)
        }
    }

    override fun visitFilterProjection(expression: FilterProjectionExpression) {
        println("Visited $expression")
        TODO("Not yet implemented")
    }

    override fun visitFlatten(expression: FlattenExpression) {
        println("Visited $expression")
        TODO("Not yet implemented")
    }

    override fun visitFunction(expression: FunctionExpression) {
        println("Visited $expression")
        TODO("Not yet implemented")
    }

    override fun visitIndex(expression: IndexExpression) {
        println("Visited $expression")
        TODO("Not yet implemented")
    }

    override fun visitLiteral(expression: LiteralExpression) {
        println("Visited $expression")
        TODO("Not yet implemented")
    }

    override fun visitMultiSelectHash(expression: MultiSelectHashExpression) {
        println("Visited $expression")
        TODO("Not yet implemented")
    }

    override fun visitMultiSelectList(expression: MultiSelectListExpression) {
        println("Visited $expression")
        TODO("Not yet implemented")
    }

    override fun visitNot(expression: NotExpression) {
        println("Visited $expression")
        TODO("Not yet implemented")
    }

    override fun visitObjectProjection(expression: ObjectProjectionExpression) {
        println("Visited $expression")
        TODO("Not yet implemented")
    }

    override fun visitOr(expression: OrExpression) {
        println("Visited $expression")
        TODO("Not yet implemented")
    }

    override fun visitProjection(expression: ProjectionExpression) {
        println("Visited $expression")
        TODO("Not yet implemented")
    }

    override fun visitSlice(expression: SliceExpression) {
        println("Visited $expression")
        TODO("Not yet implemented")
    }

    override fun visitSubexpression(expression: Subexpression) {
        println("Visited $expression")

        val left = expression.left!!
        left.accept(this)

        when (val right = expression.right!!) {
            is FieldExpression -> {
                val leftTempVar = tempVars.getValue(left)
                val operator = if (leftTempVar.symbol.isBoxed) "?." else "."
                val rightMemberShape = (leftTempVar.shape as StructureShape).expectMember(right.name)
                val rightMemberName = symbolProvider.toMemberName(rightMemberShape)

                val rightTargetShape = model.expectShape(rightMemberShape.target)
                val rightTargetSymbol = symbolProvider.toSymbol(rightTargetShape)

                val codegen = "${leftTempVar.name}$operator$rightMemberName"
                addTempVar(expression, rightMemberName, codegen, rightTargetShape, rightTargetSymbol)
            }
            else -> throw CodegenException("Unknown subexpression identifier: $right")
        }
    }
}

private fun StructureShape.expectMember(name: String): MemberShape =
    getMember(name).orElseThrow { CodegenException("Cannot find member $name on $this") }

private data class TempVar(val name: String, val codegenValue: String, val shape: Shape, val symbol: Symbol)
