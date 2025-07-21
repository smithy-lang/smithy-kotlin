package software.amazon.smithy.kotlin.codegen.service.contraints

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.model.traits.PatternTrait

internal class PatternConstraint(val memberPrefix: String, val memberName: String, val trait: PatternTrait, val pkgName: String, val writer: KotlinWriter) : AbstractConstraintTrait() {
    override fun render() {
        writer.write("if (${memberPrefix}$memberName == null) { return }")
        writer.withBlock("require(${memberPrefix}$memberName is String) {", "}") {
            write("\"Pattern trait supports only String, but type \${${memberPrefix}$memberName?.javaClass?.simpleName ?: #S} was given\"", "null")
        }
        writer.write("val ${memberName}Pattern = #S", trait.pattern)
            .write("val ${memberName}Regex = Regex(${memberName}Pattern)")
            .write("require(${memberName}Regex.containsMatchIn(${memberPrefix}$memberName)) { #S }", "Value `\${${memberPrefix}$memberName}` does not match required pattern: `\$${memberName}Pattern`")
    }
}
