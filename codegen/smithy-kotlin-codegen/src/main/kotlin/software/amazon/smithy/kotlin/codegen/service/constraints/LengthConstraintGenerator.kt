package software.amazon.smithy.kotlin.codegen.service.constraints

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.service.ServiceTypes
import software.amazon.smithy.model.traits.LengthTrait

internal class LengthConstraintGenerator(val memberPrefix: String, val memberName: String, val trait: LengthTrait, val pkgName: String, val writer: KotlinWriter) : AbstractConstraintTraitGenerator() {
    override fun render() {
        val min = trait.min.orElse(null)
        val max = trait.max.orElse(null)
        val member = "$memberPrefix$memberName"

        if (max != null && min != null) {
            writer.write("require(#T($member) in $min..$max) { #S }", ServiceTypes(pkgName).sizeOf, "The size of `$memberName` must be between $min and $max (inclusive)")
        } else if (max != null) {
            writer.write("require(#T($member) <= $max) { #S }", ServiceTypes(pkgName).sizeOf, "The size of `$memberName` must be less than or equal to $max")
        } else {
            writer.write("require(#T($member) >= $min) { #S }", ServiceTypes(pkgName).sizeOf, "The size of `$memberName` must be greater than or equal to $min")
        }
    }
}
