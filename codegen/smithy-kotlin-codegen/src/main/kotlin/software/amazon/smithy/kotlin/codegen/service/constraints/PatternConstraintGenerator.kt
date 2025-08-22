package software.amazon.smithy.kotlin.codegen.service.constraints

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.model.traits.PatternTrait

internal class PatternConstraintGenerator(val memberPrefix: String, val memberName: String, val trait: PatternTrait, val pkgName: String, val writer: KotlinWriter) : AbstractConstraintTraitGenerator() {
    override fun render() {
        val member = "$memberPrefix$memberName"

        writer.write("require(Regex(#S).containsMatchIn($member)) { #S }", trait.pattern.toString(), "Value `\${$member}` does not match required pattern: `${trait.pattern}`")
    }
}
