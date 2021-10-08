/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering

import software.amazon.smithy.jmespath.ExpressionVisitor
import software.amazon.smithy.jmespath.JmespathExpression
import software.amazon.smithy.jmespath.RuntimeType
import software.amazon.smithy.jmespath.ast.*
import software.amazon.smithy.kotlin.codegen.core.RenderingContext
import software.amazon.smithy.kotlin.codegen.core.defaultName
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.waiters.Matcher
import software.amazon.smithy.waiters.PathMatcher
import software.amazon.smithy.waiters.WaitableTrait
import software.amazon.smithy.waiters.Waiter

class WaiterGenerator(
    private val ctx: RenderingContext<OperationShape>,
    private val operation: OperationShape,
    private val waitable: WaitableTrait,
    private val name: String,
    private val waiter: Waiter,
) {
    private val writer = ctx.writer

    fun render() {
        writer.write("")
        writer.withBlock("// insert waiter #L.#L {", "// }", operation.defaultName(), name) {
            waiter.acceptors.forEach { acceptor ->
                writer.withBlock("// #L when {", "// }", acceptor.state.name) {
                    val condition = when (val matcher = acceptor.matcher) {
                        is Matcher.SuccessMember -> if (matcher.value) "success" else "failure"
                        is Matcher.ErrorTypeMember -> "exception ${matcher.value}"
                        is Matcher.OutputMember -> toFriendlyString(matcher.value)
                        else -> { throw IllegalArgumentException("Unknown matcher type ${acceptor.matcher.javaClass}") }
                    }
                    writer.write("// on #L", condition)
                }
            }
        }
    }

    private fun toFriendlyString(pathMatcher: PathMatcher): String {
        val path = JmespathExpression.parse(pathMatcher.path)
        return "${pathMatcher.path} ($path) ${pathMatcher.comparator} ${pathMatcher.expected}"
    }
}

private class CodegenExpressionVisitor : ExpressionVisitor<String> {
    override fun visitComparator(expression: ComparatorExpression): String =
        expression.left.accept(this) + " " + expression.comparator.toString() + " " + expression.right.accept(this)

    override fun visitCurrentNode(expression: CurrentExpression): String {
        TODO("Not yet implemented")
    }

    override fun visitExpressionType(expression: ExpressionTypeExpression): String = TODO("Not yet implemented")

    override fun visitFlatten(expression: FlattenExpression): String {
        TODO("Not yet implemented")
    }

    override fun visitFunction(expression: FunctionExpression): String = when (expression.name) {
        "contains" -> expression.arguments[1].accept(this) + " in " + expression.arguments[0].accept(this)
        "length" -> expression.arguments[0].accept(this) + ".size"
        else -> TODO("Can't yet handle other functions")
    }

    override fun visitField(expression: FieldExpression): String = expression.name // TODO: handle member renaming

    override fun visitIndex(expression: IndexExpression): String = TODO("Not yet implemented")

    override fun visitLiteral(expression: LiteralExpression): String = when (expression.type) {
        RuntimeType.BOOLEAN,
        RuntimeType.NUMBER -> expression.value.toString()
        RuntimeType.STRING -> "\"" + expression.value.toString().escape() + "\""
        else -> TODO("Can't yet handle other types")
    }

    override fun visitMultiSelectList(expression: MultiSelectListExpression): String {
        TODO("Not yet implemented")
    }

    override fun visitMultiSelectHash(expression: MultiSelectHashExpression): String {
        TODO("Not yet implemented")
    }

    override fun visitAnd(expression: AndExpression): String {
        TODO("Not yet implemented")
    }

    override fun visitOr(expression: OrExpression): String {
        TODO("Not yet implemented")
    }

    override fun visitNot(expression: NotExpression): String {
        TODO("Not yet implemented")
    }

    override fun visitProjection(expression: ProjectionExpression): String {
        TODO("Not yet implemented")
    }

    override fun visitFilterProjection(expression: FilterProjectionExpression): String {
        TODO("Not yet implemented")
    }

    override fun visitObjectProjection(expression: ObjectProjectionExpression): String {
        TODO("Not yet implemented")
    }

    override fun visitSlice(expression: SliceExpression): String {
        TODO("Not yet implemented")
    }

    override fun visitSubexpression(expression: Subexpression): String {
        TODO("Not yet implemented")
    }
}

private fun String.escape() = this
    .replace("\\", "\\\\")
    .replace("\t", "\\t")
    .replace("\b", "\\b")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace("\$", "\\$")
    .replace("\'", "\\'")
    .replace("\"", "\\\"")
