package software.amazon.smithy.kotlin.codegen.service.contraints

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.service.ServiceTypes
import software.amazon.smithy.model.traits.UniqueItemsTrait

internal class UniqueItemsConstraint(val memberName: String, val trait: UniqueItemsTrait, val pkgName: String, val writer: KotlinWriter) : AbstractConstraintTrait() {
    override fun render() {
        writer.withBlock("require(data.$memberName is List<*>) {", "}") {
            write("\"Unique items trait supports only List, but type \${data.$memberName?.javaClass?.simpleName ?: #S} was given\"", "null")
        }
        writer.write("require(#T(data.$memberName)) { #S }", ServiceTypes(pkgName).hasAllUniqueElements, "$memberName must have unique items")
    }
}
