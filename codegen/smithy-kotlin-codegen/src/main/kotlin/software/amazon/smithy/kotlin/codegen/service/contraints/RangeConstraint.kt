package software.amazon.smithy.kotlin.codegen.service.contraints

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.model.traits.RangeTrait

internal class RangeConstraint(val memberPrefix: String, val memberName: String, val trait: RangeTrait, val pkgName: String, val writer: KotlinWriter) : AbstractConstraintTrait() {
    override fun render() {
        val min = trait.min.orElse(null)
        val max = trait.max.orElse(null)
        writer.write("if (${memberPrefix}$memberName == null) { return }")
        writer.withBlock("require(${memberPrefix}$memberName is Number) {", "}") {
            write("\"The `range` trait can be applied only to Number, but variable `$memberName` is of type `\${${memberPrefix}$memberName?.javaClass?.simpleName ?: #S}`.\"", "null")
        }

        if (max != null && min != null) {
            writer.write("require(${memberPrefix}$memberName in $min..$max) { #S }", "$memberName must be between $min and $max")
        } else if (max != null) {
            writer.write("require(${memberPrefix}$memberName <= $max) { #S }", "$memberName must be less than or equal to $max")
        } else {
            writer.write("require(${memberPrefix}$memberName >= $min) { #S }", "$memberName must be greater than or equal to $min")
        }
    }
}
