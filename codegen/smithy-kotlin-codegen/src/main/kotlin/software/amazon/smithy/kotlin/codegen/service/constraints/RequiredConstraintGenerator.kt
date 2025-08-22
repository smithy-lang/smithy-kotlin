package software.amazon.smithy.kotlin.codegen.service.constraints

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.model.traits.RequiredTrait

internal class RequiredConstraintGenerator(val memberPrefix: String, val memberName: String, val trait: RequiredTrait, val pkgName: String, val writer: KotlinWriter) : AbstractConstraintTraitGenerator() {
    override fun render() {
        val member = "$memberPrefix$memberName"
        writer.write("require($member != null) { #S }", "`$memberName` must be provided")
    }
}
