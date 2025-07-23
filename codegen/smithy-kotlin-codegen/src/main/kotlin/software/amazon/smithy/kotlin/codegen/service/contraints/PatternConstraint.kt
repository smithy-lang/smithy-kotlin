package software.amazon.smithy.kotlin.codegen.service.contraints

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.model.traits.PatternTrait

internal class PatternConstraint(val memberPrefix: String, val memberName: String, val trait: PatternTrait, val pkgName: String, val writer: KotlinWriter) : AbstractConstraintTrait() {
    override fun render() {
        writer.write("if (${memberPrefix}$memberName == null) { return }")
        writer.withBlock("require(${memberPrefix}$memberName is String) {", "}") {
            write("\"The `pattern` trait can be applied only to String, but variable `$memberName` is of type `\${${memberPrefix}$memberName?.javaClass?.simpleName ?: #S}`.\"", "null")
        }
        writer.write("require(Regex(#S).containsMatchIn(${memberPrefix}$memberName)) { #S }", trait.pattern.toString(), "Value `\${${memberPrefix}$memberName}` does not match required pattern: `${trait.pattern}`")
    }
}
