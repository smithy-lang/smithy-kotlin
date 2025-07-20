package software.amazon.smithy.kotlin.codegen.service.contraints

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.model.traits.PatternTrait

internal class PatternConstraint(val memberName: String, val trait: PatternTrait, val pkgName: String, val writer: KotlinWriter) : AbstractConstraintTrait() {
    override fun render() {
        writer.withBlock("require(data.$memberName is String) {", "}") {
            write("\"Pattern trait supports only String, but type \${data.$memberName?.javaClass?.simpleName ?: #S} was given\"", "null")
        }
        writer.write("val pattern = #S", trait.pattern)
            .write("val regex = Regex(pattern)")
            .write("require(regex.matches(data.$memberName)) { #S }", "Value `\${data.$memberName}` does not match required pattern: `\$pattern`")
    }
}
