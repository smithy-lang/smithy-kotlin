package software.amazon.smithy.kotlin.codegen.service.contraints

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.service.ServiceTypes
import software.amazon.smithy.model.traits.UniqueItemsTrait

internal class UniqueItemsConstraint(val memberPrefix: String, val memberName: String, val trait: UniqueItemsTrait, val pkgName: String, val writer: KotlinWriter) : AbstractConstraintTrait() {
    override fun render() {
        writer.write("if (${memberPrefix}$memberName == null) { return }")
        writer.withBlock("require(${memberPrefix}$memberName is List<*>) {", "}") {
            write("\"The `uniqueItems` trait can be applied only to List, but variable `$memberName` is of type `\${${memberPrefix}$memberName?.javaClass?.simpleName ?: #S}`.\"", "null")
        }
        writer.write("require(#T(${memberPrefix}$memberName)) { #S }", ServiceTypes(pkgName).hasAllUniqueElements, "$memberName must have unique items")
    }
}
