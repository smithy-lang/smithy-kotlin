/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.endpoints

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.model.SourceLocation
import software.amazon.smithy.rulesengine.language.EndpointRuleSet
import software.amazon.smithy.rulesengine.language.stdlib.BooleanEquals
import software.amazon.smithy.rulesengine.language.stdlib.StringEquals
import software.amazon.smithy.rulesengine.language.syntax.Identifier
import software.amazon.smithy.rulesengine.language.syntax.expr.Expression
import software.amazon.smithy.rulesengine.language.syntax.expr.Literal
import software.amazon.smithy.rulesengine.language.syntax.expr.Reference
import software.amazon.smithy.rulesengine.language.syntax.expr.Template
import software.amazon.smithy.rulesengine.language.syntax.fn.*
import software.amazon.smithy.rulesengine.language.syntax.fn.Function
import software.amazon.smithy.rulesengine.language.syntax.rule.Condition
import software.amazon.smithy.rulesengine.language.syntax.rule.EndpointRule
import software.amazon.smithy.rulesengine.language.syntax.rule.ErrorRule
import software.amazon.smithy.rulesengine.language.syntax.rule.Rule
import software.amazon.smithy.rulesengine.language.syntax.rule.TreeRule
import software.amazon.smithy.rulesengine.language.visit.TemplateVisitor

/**
 * Renders the default endpoint provider based on the provided rule set.
 */
class DefaultEndpointProviderGenerator(
    private val writer: KotlinWriter,
    private val rules: EndpointRuleSet,
    private val interfaceSymbol: Symbol,
    private val paramsSymbol: Symbol,
) : Literal.Vistor<Unit>, TemplateVisitor<Unit> {
    companion object {
        const val CLASS_NAME = "DefaultEndpointProvider"

        fun getSymbol(ctx: CodegenContext): Symbol =
            buildSymbol {
                name = CLASS_NAME
                namespace = "${ctx.settings.pkg.name}.endpoints"
            }
    }

    private val functions: Map<String, Symbol> = mapOf(
        "substring" to RuntimeTypes.Http.Endpoints.Functions.substring,
        "isValidHostLabel" to RuntimeTypes.Http.Endpoints.Functions.isValidHostLabel,
        "uriEncode" to RuntimeTypes.Http.Endpoints.Functions.uriEncode,
        "parseURL" to RuntimeTypes.Http.Endpoints.Functions.parseUrl,
        "aws.partition" to buildSymbol { name = "partition" }, // the partition function is generated per-service in the same package
        "aws.parseArn" to RuntimeTypes.Http.Endpoints.Functions.parseArn,
        "aws.isVirtualHostableS3Bucket" to RuntimeTypes.Http.Endpoints.Functions.isVirtualHostableS3Bucket,
    )

    fun render() {
        renderDocumentation()
        writer.withBlock("public class #L: #T {", "}", CLASS_NAME, interfaceSymbol) {
            renderResolve()
        }
    }

    private fun renderDocumentation() {
        writer.dokka {
            write("The default endpoint provider as specified by the service model.")
        }
    }

    private fun renderResolve() {
        writer.withBlock(
            "public override suspend fun resolveEndpoint(params: #T): #T {",
            "}",
            paramsSymbol,
            RuntimeTypes.Http.Endpoints.Endpoint,
        ) {
            rules.rules.forEach(::renderRule)
            write("")
            write("throw #T(\"endpoint rules were exhausted without a match\")", RuntimeTypes.Http.Endpoints.EndpointProviderException)
        }
    }

    private fun renderRule(rule: Rule) {
        when (rule) {
            is EndpointRule -> {
                renderEndpointRule(rule)
            }
            is ErrorRule -> {
                renderErrorRule(rule)
            }
            is TreeRule -> {
                renderTreeRule(rule)
            }
            else -> throw IllegalArgumentException("unexpected rule")
        }
    }

    private fun writeFunction(fn: Function) {
        when (fn) {
            // fulfilled as inline kotlin expressions
            is IsSet -> {
                writeExpression(fn.target)
                writer.writeInline(" != null")
            }
            is Not -> {
                writer.writeInline("!(")
                writeExpression(fn.target)
                writer.writeInline(")")
            }
            is StringEquals, is BooleanEquals -> {
                writeExpression(fn.arguments[0])
                writer.writeInline(" == ")
                writeExpression(fn.arguments[1])
            }
            // symbol-based function calls: delegate to resolveFunction() and blindly render arguments
            is LibraryFunction -> {
                writer.writeInline("#T(", functions.getValue(fn.name))
                fn.arguments.forEachIndexed { index, it ->
                    writeExpression(it)
                    if (index < fn.arguments.lastIndex) {
                        writer.writeInline(", ")
                    }
                }
                writer.writeInline(")")
            }
            else -> throw IllegalArgumentException("unexpected function")
        }
    }

    private fun writeExpression(expr: Expression) {
        when (expr) {
            is Reference -> writeReference(expr)
            is Literal -> expr.accept(this)
            is Function -> writeFunction(expr)
            is GetAttr -> writeGetAttr(expr)
            else -> throw IllegalArgumentException("unexpected expression")
        }
    }

    private fun writeGetAttr(expr: GetAttr) {
        writeExpression(expr.target)
        expr.path.forEach {
            when (it) {
                is GetAttr.Part.Key -> writer.writeInline("?.#L", it.key().asString())
                is GetAttr.Part.Index -> writer.writeInline("?.getOrNull(#L)", it.index())
                else -> throw IllegalArgumentException("unexpected path")
            }
        }
    }

    private fun writeReference(ref: Reference) {
        if (isParamRef(ref)) {
            writer.writeInline("params.")
        }
        writer.writeInline(ref.name.toKotlin())
    }

    private fun isParamRef(ref: Reference): Boolean = rules.parameters.toList().any { it.name == ref.name }

    private fun withConditions(conditions: List<Condition>, block: () -> Unit) {
        val (assignments, expressions) = conditions.partition()

        writer.wrapBlockIf(assignments.isNotEmpty(), "run {", "}") {
            assignments.forEach {
                writer.writeInline("val #L = ", it.result.get().toKotlin())
                writeExpression(it.fn)
                writer.write("")
            }
            if (expressions.isNotEmpty()) {
                writer.openBlock("if (")
                expressions.forEachIndexed { index, it ->
                    writeExpression(it.fn)
                    if (!it.fn.isBooleanFunction()) { // these are meant to be evaluated on "truthiness" (i.e. is the result non-null)
                        writeInline(" != null")
                    }
                    write(if (index == expressions.lastIndex) "" else " &&")
                }
                writer.closeAndOpenBlock(") {")
            }
            block()
            if (expressions.isNotEmpty()) {
                writer.closeBlock("}")
            }
        }
    }

    private fun renderEndpointRule(rule: EndpointRule) {
        withConditions(rule.conditions) {
            writer.withBlock("return #T(", ")", RuntimeTypes.Http.Endpoints.Endpoint) {
                writeInline("#T.parse(", RuntimeTypes.Http.Url)
                writeExpression(rule.endpoint.url)
                write("),")

                if (rule.endpoint.headers.isNotEmpty()) {
                    withBlock("headers = #T {", "},", RuntimeTypes.Http.Headers) {
                        rule.endpoint.headers.entries.forEach { (k, v) ->
                            v.forEach {
                                writeInline("append(#S, ", k)
                                writeExpression(it)
                                write(")")
                            }
                        }
                    }
                }

                if (rule.endpoint.properties.isNotEmpty()) {
                    withBlock("attributes = #T().apply {", "},", RuntimeTypes.Utils.Attributes) {
                        rule.endpoint.properties.entries.forEach { (k, v) ->
                            writeInline("set(#T(#S), ", RuntimeTypes.Utils.AttributeKey, k.asString())
                            writeExpression(v)
                            write(")")
                        }
                    }
                }
            }
        }
    }

    private fun renderErrorRule(rule: ErrorRule) {
        withConditions(rule.conditions) {
            writer.writeInline("throw #T(", RuntimeTypes.Http.Endpoints.EndpointProviderException)
            writeExpression(rule.error)
            writer.write(")")
        }
    }

    private fun renderTreeRule(rule: TreeRule) {
        withConditions(rule.conditions) {
            rule.rules.forEach(::renderRule)
        }
    }

    override fun visitInteger(value: Int) {
        writer.writeInline("#L", value)
    }

    override fun visitString(value: Template) {
        writer.writeInline("\"")
        value.accept(this).toList() // must "consume" the stream to actually generate everything
        writer.writeInline("\"")
    }

    override fun visitBool(value: Boolean) {
        writer.writeInline("#L", value)
    }

    override fun visitRecord(value: MutableMap<Identifier, Literal>) {
        writer.withBlock("#T().apply {", "}", RuntimeTypes.Utils.Attributes) {
            value.entries.forEach { (k, v) ->
                writeInline("set(#T(#S), ", RuntimeTypes.Utils.AttributeKey, k.asString())
                v.accept(this@DefaultEndpointProviderGenerator)
                write(")")
            }
        }
    }

    override fun visitTuple(value: MutableList<Literal>) {
        if (value.size == 1) { // unclear why this is a thing
            value[0].accept(this)
            return
        }

        writer.withBlock("listOf(", ")") {
            value.forEach {
                it.accept(this@DefaultEndpointProviderGenerator)
                write(",")
            }
        }
    }

    override fun visitStaticTemplate(value: String) = writeTemplateString(value)
    override fun visitSingleDynamicTemplate(value: Expression) = writeTemplateExpression(value)
    override fun visitStaticElement(value: String) = writeTemplateString(value)
    override fun visitDynamicElement(value: Expression) = writeTemplateExpression(value)

    // no-ops for kotlin codegen
    override fun startMultipartTemplate() {}
    override fun finishMultipartTemplate() {}

    private fun writeTemplateString(value: String) {
        writer.writeInline(value)
    }

    private fun writeTemplateExpression(expr: Expression) {
        writer.writeInline("\${")
        writeExpression(expr)
        writer.writeInline("}")
    }
}

// splits a list of conditions into a set of assignments and expressions
// adds "implicit" isSet (x != null) checks that must be evaluated for each assignment
private fun List<Condition>.partition(): Pair<List<Condition>, List<Condition>> {
    val (assignments, expressions) = partition { it.result.isPresent }
    val implicitExpressions = assignments.map(Condition::buildResultIsSetExpression)
    return Pair(assignments, implicitExpressions + expressions)
}

// build an "isSet" expression that checks the nullness of the result of an assignment operation
private fun Condition.buildResultIsSetExpression() =
    Condition.Builder()
        .fn(
            IsSet.ofExpression(
                Reference(result.get(), SourceLocation.NONE),
            ),
        )
        .build()

private fun Expression.isBooleanFunction(): Boolean {
    if (this !is LibraryFunction) {
        return true
    }

    return name != "parseUrl" &&
        name != "substring" &&
        name != "uriEncode" &&
        name != "aws.parseArn" &&
        name != "aws.partition"
}
