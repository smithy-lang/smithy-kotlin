package software.amazon.smithy.kotlin.codegen.service.contraints

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.service.ServiceTypes
import software.amazon.smithy.model.traits.LengthTrait

internal class LengthConstraint(val memberPrefix: String, val memberName: String, val trait: LengthTrait, val pkgName: String, val writer: KotlinWriter) : AbstractConstraintTrait() {
    override fun render() {
        val min = trait.min.orElse(null)
        val max = trait.max.orElse(null)
        writer.write("if (${memberPrefix}$memberName == null) { return }")
        writer.withBlock("require(${memberPrefix}$memberName is List<*> || ${memberPrefix}$memberName is Map<*, *> || ${memberPrefix}$memberName is String || ${memberPrefix}$memberName is java.sql.Blob) {", "}") {
            write("\"Length trait supports only List, Map, String, or Blob, but type \${${memberPrefix}$memberName?.javaClass?.simpleName ?: #S} was given\"", "null")
        }

        if (max != null && min != null) {
            writer.write("require(#T(${memberPrefix}$memberName) in $min..$max) { #S }", ServiceTypes(pkgName).sizeOf, "$memberName\'s size must be between $min and $max")
        } else if (max != null) {
            writer.write("require(#T(${memberPrefix}$memberName) <= $max) { #S }", ServiceTypes(pkgName).sizeOf, "$memberName\'s size must be less than or equal to $max")
        } else {
            writer.write("require(#T(${memberPrefix}$memberName) >= $min) { #S }", ServiceTypes(pkgName).sizeOf, "$memberName\'s size must be greater than or equal to $min")
        }
    }
}
