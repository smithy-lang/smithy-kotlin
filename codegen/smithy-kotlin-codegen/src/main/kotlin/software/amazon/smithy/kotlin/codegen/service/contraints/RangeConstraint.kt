package software.amazon.smithy.kotlin.codegen.service.contraints

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.model.traits.RangeTrait

internal class RangeConstraint(val memberName: String, val trait: RangeTrait, val pkgName: String, val writer: KotlinWriter) : AbstractConstraintTrait() {
    override fun render() {
        val min = trait.min.orElse(null)
        val max = trait.max.orElse(null)

        writer.withBlock("require(data.$memberName is Number) {", "}") {
            write("\"Range trait supports only Number, but type \${data.$memberName?.javaClass?.simpleName ?: #S} was given\"", "null")
        }

        if (max != null && min != null) {
            writer.write("require(data.$memberName in $min..$max) { #S }", "$memberName must be between $min and $max")
        } else if (max != null) {
            writer.write("require(data.$memberName <= $max) { #S }", "$memberName must be less than or equal to $max")
        } else {
            writer.write("require(data.$memberName >= $min) { #S }", "$memberName must be greater than or equal to $min")
        }
    }
}
