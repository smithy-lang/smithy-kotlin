package software.amazon.smithy.kotlin.codegen.service.contraints

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.service.ServiceTypes
import software.amazon.smithy.model.traits.UniqueItemsTrait

internal class UniqueItemsConstraintGenerator(val memberPrefix: String, val memberName: String, val trait: UniqueItemsTrait, val pkgName: String, val writer: KotlinWriter) : AbstractConstraintTraitGenerator() {
    override fun render() {
        val member = "$memberPrefix$memberName"
        writer.write("require(#T($member)) { #S }", ServiceTypes(pkgName).hasAllUniqueElements, "`$memberName` must contain only unique items, duplicate values are not allowed")
    }
}
