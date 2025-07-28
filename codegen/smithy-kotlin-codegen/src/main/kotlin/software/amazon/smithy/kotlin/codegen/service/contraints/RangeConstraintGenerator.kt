package software.amazon.smithy.kotlin.codegen.service.contraints

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.model.traits.RangeTrait

internal class RangeConstraintGenerator(val memberPrefix: String, val memberName: String, val trait: RangeTrait, val pkgName: String, val writer: KotlinWriter) : AbstractConstraintTraitGenerator() {
    override fun render() {
        val min = trait.min.orElse(null)
        val max = trait.max.orElse(null)
        val member = "$memberPrefix$memberName"

        if (max != null && min != null) {
            writer.write("require($member in $min..$max) { #S }", "`$memberName` must be between $min and $max (inclusive)")
        } else if (max != null) {
            writer.write("require($member <= $max) { #S }", "`$memberName` must be less than or equal to $max")
        } else {
            writer.write("require($member >= $min) { #S }", "`$memberName` must be greater than or equal to $min")
        }
    }
}
