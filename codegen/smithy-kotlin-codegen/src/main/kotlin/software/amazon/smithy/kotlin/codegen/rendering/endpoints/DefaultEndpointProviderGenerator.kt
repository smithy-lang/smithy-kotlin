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
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
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
import java.util.stream.Collectors

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
    fun renderExpression(expr: Expression): EndpointInfo
}

/**
 * Renders the default endpoint provider based on the provided rule set.
 */
class DefaultEndpointProviderGenerator(
    private val ctx: ProtocolGenerator.GenerationContext,
    private val rules: EndpointRuleSet,
    private val writer: KotlinWriter,
) : ExpressionRenderer {
    companion object {
        fun getSymbol(settings: KotlinSettings): Symbol =
            buildSymbol {
                val prefix = clientName(settings.sdkId)
                name = "Default${prefix}EndpointProvider"
                namespace = "${settings.pkg.name}.endpoints"
            }
    }

    private val endpointCustomizations = ctx.integrations.mapNotNull { it.customizeEndpointResolution(ctx) }

    private val externalFunctions = endpointCustomizations
        .map { it.externalFunctions }
        .fold(mutableMapOf<String, Symbol>()) { acc, extFunctions ->
            acc.putAll(extFunctions)
            acc
        }.toMap()

    private val propertyRenderers = endpointCustomizations
        .map { it.propertyRenderers }
        .fold(mutableMapOf<String, MutableList<EndpointPropertyRenderer>>()) { acc, propRenderers ->
            propRenderers.forEach { (key, propRenderer) ->
                acc[key] = acc.getOrDefault(key, mutableListOf()).also { it.add(propRenderer) }
            }
            acc
        }

    private val expressionGenerator = ExpressionGenerator(writer, rules, coreFunctions + externalFunctions)

    private val defaultProviderSymbol = getSymbol(ctx.settings)
    private val interfaceSymbol = EndpointProviderGenerator.getSymbol(ctx.settings)
    private val paramsSymbol = EndpointParametersGenerator.getSymbol(ctx.settings)

    fun render() {
        renderDocumentation()
        writer.withBlock(
            "#L class #T: #T {",
            "}",
            ctx.settings.api.visibility,
            defaultProviderSymbol,
            interfaceSymbol,
        ) {
            renderResolve()
        }
    }

    override fun renderExpression(expr: Expression): EndpointInfo = expr.accept(expressionGenerator) ?: EndpointInfo.Empty

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
                writeInline("#T.parse(", RuntimeTypes.Core.Net.Url.Url)
                val endpointInfo = renderExpression(rule.endpoint.url)
                write("),")

                val hasAccountIdBasedEndpoint = "accountId" in endpointInfo.params
                val hasEndpointOverride = "endpoint" in endpointInfo.params
                val needAdditionalEndpointProperties = hasAccountIdBasedEndpoint || hasEndpointOverride

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

                if (rule.endpoint.properties.isNotEmpty() || needAdditionalEndpointProperties) {
                    withBlock("attributes = #T {", "},", RuntimeTypes.Core.Collections.attributesOf) {
                        rule.endpoint.properties.entries.forEach { (k, v) ->
                            val kStr = k.toString()

                            // caller has a chance to generate their own value for a recognized property
                            if (kStr in propertyRenderers) {
                                propertyRenderers[kStr]!!.forEach { renderer ->
                                    renderer(writer, v, this@DefaultEndpointProviderGenerator)
                                }
                                return@forEach
                            }

                            // otherwise, we just traverse the value like any other rules expression, object values will
                            // be rendered as Documents
                            writeInline("#S to ", kStr)
                            renderExpression(v)
                            ensureNewline()
                        }

                        if (hasAccountIdBasedEndpoint) {
                            writer.write("#T to params.accountId", RuntimeTypes.Core.BusinessMetrics.AccountIdBasedEndpointAccountId)
                        }
                        if (hasEndpointOverride) {
                            writer.write("#T to true", RuntimeTypes.Core.BusinessMetrics.EndpointOverride)
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

data class EndpointInfo(val params: MutableSet<String>) {
    companion object {
        val Empty = EndpointInfo(params = mutableSetOf())
    }

    operator fun plus(other: EndpointInfo) = EndpointInfo(
        params = (this.params + other.params).toMutableSet(),
    )
}

class ExpressionGenerator(
    private val writer: KotlinWriter,
    private val rules: EndpointRuleSet,
    private val functions: Map<String, Symbol>,
) : ExpressionVisitor<EndpointInfo?>,
    LiteralVisitor<EndpointInfo?>,
    TemplateVisitor<EndpointInfo?> {
    override fun visitLiteral(literal: Literal): EndpointInfo? = literal.accept(this as LiteralVisitor<EndpointInfo?>)

    override fun visitRef(reference: Reference): EndpointInfo {
        val referenceName = reference.name.defaultName()
        val isParamReference = isParamRef(reference)

        if (isParamReference) {
            writer.writeInline("params.")
        }
        writer.writeInline(referenceName)

        return if (isParamReference) {
            EndpointInfo(params = mutableSetOf(referenceName))
        } else {
            EndpointInfo.Empty
        }
    }

    override fun visitGetAttr(getAttr: GetAttr): EndpointInfo? {
        val endpointInfo = getAttr.target.accept(this)
        getAttr.path.forEach {
            when (it) {
                is GetAttr.Part.Key -> writer.writeInline("?.#L", it.key().toString())
                is GetAttr.Part.Index -> writer.writeInline("?.getOrNull(#L)", it.index())
                else -> throw CodegenException("unexpected path")
            }
        }
        return endpointInfo
    }

    override fun visitIsSet(target: Expression): EndpointInfo? {
        val endpointInfo = target.accept(this)
        writer.writeInline(" != null")
        return endpointInfo
    }

    override fun visitNot(target: Expression): EndpointInfo? {
        writer.writeInline("!(")
        val endpointInfo = target.accept(this)
        writer.writeInline(")")
        return endpointInfo
    }

    override fun visitBoolEquals(left: Expression, right: Expression): EndpointInfo? = visitEquals(left, right)

    override fun visitStringEquals(left: Expression, right: Expression): EndpointInfo? = visitEquals(left, right)

    private fun visitEquals(left: Expression, right: Expression): EndpointInfo? {
        val leftEndpointInfo = left.accept(this)
        writer.writeInline(" == ")
        val rightEndpointInfo = right.accept(this)

        return when {
            leftEndpointInfo != null && rightEndpointInfo != null -> leftEndpointInfo + rightEndpointInfo
            leftEndpointInfo != null -> leftEndpointInfo
            else -> rightEndpointInfo
        }
    }

    override fun visitLibraryFunction(fn: FunctionDefinition, args: MutableList<Expression>): EndpointInfo? {
        writer.writeInline("#T(", functions.getValue(fn.id))

        val endpointInfo = args.foldIndexed(EndpointInfo.Empty) { index, acc, curr ->
            val currEndpointInfo = curr.accept(this)
            if (index < args.lastIndex) {
                writer.writeInline(", ")
            }
            currEndpointInfo?.let { acc + it } ?: acc
        }
        writer.writeInline(")")
        return endpointInfo
    }

    override fun visitInteger(value: Int): EndpointInfo? {
        writer.writeInline("#L", value)
        return null
    }

    override fun visitString(value: Template): EndpointInfo? {
        writer.writeInline("\"")
        val endpointInfo = value.accept(this)
            .collect(Collectors.toList())
            .fold(EndpointInfo.Empty) { acc, curr ->
                curr?.let { acc + it } ?: acc
            }
        writer.writeInline("\"")
        return endpointInfo
    }

    override fun visitBoolean(value: Boolean): EndpointInfo? {
        writer.writeInline("#L", value)
        return null
    }

    override fun visitRecord(value: MutableMap<Identifier, Literal>): EndpointInfo? {
        var endpointInfo: EndpointInfo? = null
        writer.withInlineBlock("#T {", "}", RuntimeTypes.Core.Content.buildDocument) {
            endpointInfo = value.entries.foldIndexed(EndpointInfo.Empty) { index, acc, (k, v) ->
                writeInline("#S to ", k.toString())
                val currInfo = v.accept(this@ExpressionGenerator as LiteralVisitor<EndpointInfo?>)
                if (index < value.size - 1) write("")
                currInfo?.let { acc + it } ?: acc
            }
        }
        return endpointInfo
    }

    override fun visitTuple(value: MutableList<Literal>): EndpointInfo? {
        var endpointInfo: EndpointInfo? = null
        writer.withInlineBlock("listOf(", ")") {
            endpointInfo = value.foldIndexed(EndpointInfo.Empty) { index, acc, curr ->
                val localInfo = curr.accept(this@ExpressionGenerator as LiteralVisitor<EndpointInfo?>)
                if (index < value.size - 1) write(",") else writeInline(",")
                localInfo?.let { acc + it } ?: acc
            }
        }
        return endpointInfo
    }

    override fun visitStaticTemplate(value: String) = writeTemplateString(value)
    override fun visitSingleDynamicTemplate(value: Expression) = writeTemplateExpression(value)
    override fun visitStaticElement(value: String) = writeTemplateString(value)
    override fun visitDynamicElement(value: Expression) = writeTemplateExpression(value)

    // no-ops for kotlin codegen
    override fun startMultipartTemplate(): EndpointInfo? = null
    override fun finishMultipartTemplate(): EndpointInfo? = null

    private fun writeTemplateString(value: String): EndpointInfo? {
        writer.writeInline(value.replace("\"", "\\\""))
        return null
    }

    private fun writeTemplateExpression(expr: Expression): EndpointInfo? {
        writer.writeInline("\${")
        val endpointInfo = expr.accept(this)
        writer.writeInline("}")
        return endpointInfo
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
