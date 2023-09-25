/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.endpoints

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.model.defaultName
import software.amazon.smithy.model.SourceLocation
import software.amazon.smithy.rulesengine.language.EndpointRuleSet
import software.amazon.smithy.rulesengine.language.syntax.Identifier
import software.amazon.smithy.rulesengine.language.syntax.ToExpression
import software.amazon.smithy.rulesengine.language.syntax.expressions.*
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.*
import software.amazon.smithy.rulesengine.language.syntax.expressions.literal.Literal
import software.amazon.smithy.rulesengine.language.syntax.expressions.literal.LiteralVisitor
import software.amazon.smithy.rulesengine.language.syntax.rule.Condition
import software.amazon.smithy.rulesengine.language.syntax.rule.EndpointRule
import software.amazon.smithy.rulesengine.language.syntax.rule.ErrorRule
import software.amazon.smithy.rulesengine.language.syntax.rule.Rule
import software.amazon.smithy.rulesengine.language.syntax.rule.TreeRule

/**
 * The core set of standard library functions available to the rules language.
 */
internal val coreFunctions: Map<String, Symbol> = mapOf(
    "substring" to RuntimeTypes.SmithyClient.Endpoints.Functions.substring,
    "isValidHostLabel" to RuntimeTypes.SmithyClient.Endpoints.Functions.isValidHostLabel,
    "uriEncode" to RuntimeTypes.SmithyClient.Endpoints.Functions.uriEncode,
    "parseURL" to RuntimeTypes.SmithyClient.Endpoints.Functions.parseUrl,
)

/**
 * Defines a callback that renders an SDK-specific endpoint property.
 * The callback is passed the following:
 * - a writer to which code should be rendered
 * - the root expression construct for the property
 * - a generic expression renderer to defer back to the base implementation (for example, for handling generic sub-expressions
 *   or string templates for which the caller doesn't need to provide extended behavior)
 */
typealias EndpointPropertyRenderer = (KotlinWriter, Expression, ExpressionRenderer) -> Unit

/**
 * An expression renderer generates code for an endpoint expression construct.
 */
fun interface ExpressionRenderer {
    fun renderExpression(expr: Expression)
}

/**
 * Renders the default endpoint provider based on the provided rule set.
 */
class DefaultEndpointProviderGenerator(
    private val writer: KotlinWriter,
    private val rules: EndpointRuleSet,
    private val defaultProviderSymbol: Symbol,
    private val interfaceSymbol: Symbol,
    private val paramsSymbol: Symbol,
    private val settings: KotlinSettings,
    private val externalFunctions: Map<String, Symbol> = emptyMap(),
    private val propertyRenderers: Map<String, EndpointPropertyRenderer> = emptyMap(),
) : ExpressionRenderer {
    companion object {
        fun getSymbol(settings: KotlinSettings): Symbol =
            buildSymbol {
                val prefix = clientName(settings.sdkId)
                name = "Default${prefix}EndpointProvider"
                namespace = "${settings.pkg.name}.endpoints"
            }
    }

    private val expressionGenerator = ExpressionGenerator(writer, rules, coreFunctions + externalFunctions)

    fun render() {
        renderDocumentation()
        writer.withBlock(
            "#L class #T: #T {",
            "}",
            settings.api.visibility,
            defaultProviderSymbol,
            interfaceSymbol,
        ) {
            renderResolve()
        }
    }

    override fun renderExpression(expr: Expression) {
        expr.accept(expressionGenerator)
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
            RuntimeTypes.SmithyClient.Endpoints.Endpoint,
        ) {
            rules.rules.forEach(::renderRule)
            write("")
            write("throw #T(\"endpoint rules were exhausted without a match\")", RuntimeTypes.SmithyClient.Endpoints.EndpointProviderException)
        }
    }

    private fun renderRule(rule: Rule) {
        when (rule) {
            is EndpointRule -> renderEndpointRule(rule)
            is ErrorRule -> renderErrorRule(rule)
            is TreeRule -> renderTreeRule(rule)
            else -> throw CodegenException("unexpected rule")
        }
    }

    private fun withConditions(conditions: List<Condition>, block: () -> Unit) {
        val (assignments, expressions) = conditions.partition()

        // explicitly wrap blocks with assignments to restrict scope therein
        writer.wrapBlockIf(assignments.isNotEmpty(), "run {", "}") {
            assignments.forEach {
                writer.writeInline("val #L = ", it.result.get().defaultName())
                renderExpression(it.function)
                writer.write("")
            }
            if (expressions.isNotEmpty()) {
                writer.openBlock("if (")
                expressions.forEachIndexed { index, it ->
                    renderExpression(it.function)
                    if (!it.function.isBooleanFunction()) { // these are meant to be evaluated on "truthiness" (i.e. is the result non-null)
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
            writer.withBlock("return #T(", ")", RuntimeTypes.SmithyClient.Endpoints.Endpoint) {
                writeInline("#T.parse(", RuntimeTypes.Core.Net.Url)
                renderExpression(rule.endpoint.url)
                write(", #1T.DecodeAll - #1T.DecodePath),", RuntimeTypes.Core.Net.UrlDecoding)

                if (rule.endpoint.headers.isNotEmpty()) {
                    withBlock("headers = #T {", "},", RuntimeTypes.Http.Headers) {
                        rule.endpoint.headers.entries.forEach { (k, v) ->
                            v.forEach {
                                writeInline("append(#S, ", k)
                                renderExpression(it)
                                write(")")
                            }
                        }
                    }
                }

                if (rule.endpoint.properties.isNotEmpty()) {
                    withBlock("attributes = #T {", "},", RuntimeTypes.Core.Utils.attributesOf) {
                        rule.endpoint.properties.entries.forEach { (k, v) ->
                            val kStr = k.toString()

                            // caller has a chance to generate their own value for a recognized property
                            if (kStr in propertyRenderers) {
                                propertyRenderers[kStr]!!(writer, v, this@DefaultEndpointProviderGenerator)
                                return@forEach
                            }

                            // otherwise, we just traverse the value like any other rules expression, object values will
                            // be rendered as Documents
                            writeInline("#T(#S) to ", RuntimeTypes.Core.Utils.AttributeKey, kStr)
                            renderExpression(v)
                            ensureNewline()
                        }
                    }
                }
            }
        }
    }

    private fun renderErrorRule(rule: ErrorRule) {
        withConditions(rule.conditions) {
            writer.writeInline("throw #T(", RuntimeTypes.SmithyClient.Endpoints.EndpointProviderException)
            renderExpression(rule.error)
            writer.write(")")
        }
    }

    private fun renderTreeRule(rule: TreeRule) {
        withConditions(rule.conditions) {
            rule.rules.forEach(::renderRule)
        }
    }
}

class ExpressionGenerator(
    private val writer: KotlinWriter,
    private val rules: EndpointRuleSet,
    private val functions: Map<String, Symbol>,
) : ExpressionVisitor<Unit>, LiteralVisitor<Unit>, TemplateVisitor<Unit> {
    override fun visitLiteral(literal: Literal) {
        literal.accept(this as LiteralVisitor<Unit>)
    }

    override fun visitRef(reference: Reference) {
        if (isParamRef(reference)) {
            writer.writeInline("params.")
        }
        writer.writeInline(reference.name.defaultName())
    }

    override fun visitGetAttr(getAttr: GetAttr) {
        getAttr.target.accept(this)
        getAttr.path.forEach {
            when (it) {
                is GetAttr.Part.Key -> writer.writeInline("?.#L", it.key().toString())
                is GetAttr.Part.Index -> writer.writeInline("?.getOrNull(#L)", it.index())
                else -> throw CodegenException("unexpected path")
            }
        }
    }

    override fun visitIsSet(target: Expression) {
        target.accept(this)
        writer.writeInline(" != null")
    }

    override fun visitNot(target: Expression) {
        writer.writeInline("!(")
        target.accept(this)
        writer.writeInline(")")
    }

    override fun visitBoolEquals(left: Expression, right: Expression) {
        visitEquals(left, right)
    }

    override fun visitStringEquals(left: Expression, right: Expression) {
        visitEquals(left, right)
    }

    private fun visitEquals(left: Expression, right: Expression) {
        left.accept(this)
        writer.writeInline(" == ")
        right.accept(this)
    }

    override fun visitLibraryFunction(fn: FunctionDefinition, args: MutableList<Expression>) {
        writer.writeInline("#T(", functions.getValue(fn.id))
        args.forEachIndexed { index, it ->
            it.accept(this)
            if (index < args.lastIndex) {
                writer.writeInline(", ")
            }
        }
        writer.writeInline(")")
    }

    override fun visitInteger(value: Int) {
        writer.writeInline("#L", value)
    }

    override fun visitString(value: Template) {
        writer.writeInline("\"")
        value.accept(this).forEach {} // must "consume" the stream to actually generate everything
        writer.writeInline("\"")
    }

    override fun visitBoolean(value: Boolean) {
        writer.writeInline("#L", value)
    }

    override fun visitRecord(value: MutableMap<Identifier, Literal>) {
        writer.withInlineBlock("#T {", "}", RuntimeTypes.Core.Content.buildDocument) {
            value.entries.forEachIndexed { index, (k, v) ->
                writeInline("#S to ", k.toString())
                v.accept(this@ExpressionGenerator as LiteralVisitor<Unit>)
                if (index < value.size - 1) write("")
            }
        }
    }

    override fun visitTuple(value: MutableList<Literal>) {
        writer.withInlineBlock("listOf(", ")") {
            value.forEachIndexed { index, it ->
                it.accept(this@ExpressionGenerator as LiteralVisitor<Unit>)
                if (index < value.size - 1) write(",") else writeInline(",")
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
        writer.writeInline(value.replace("\"", "\\\""))
    }

    private fun writeTemplateExpression(expr: Expression) {
        writer.writeInline("\${")
        expr.accept(this)
        writer.writeInline("}")
    }

    private fun isParamRef(ref: Reference): Boolean = rules.parameters.toList().any { it.name == ref.name }
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
    Condition
        .Builder()
        .fn(isSet(Reference(result.get(), SourceLocation.NONE)))
        .build()

private fun isSet(expression: Expression) =
    IsSet
        .getDefinition()
        .createFunction(FunctionNode.ofExpressions(IsSet.ID, ToExpression { expression }))

private fun Expression.isBooleanFunction(): Boolean {
    if (this !is LibraryFunction) {
        return true
    }

    return name !in setOf(
        "parseUrl",
        "substring",
        "uriEncode",
        "aws.parseArn",
        "aws.partition",
    )
}
