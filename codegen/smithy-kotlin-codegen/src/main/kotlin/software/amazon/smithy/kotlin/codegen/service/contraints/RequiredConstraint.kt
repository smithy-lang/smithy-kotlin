package software.amazon.smithy.kotlin.codegen.service.contraints

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.model.traits.RequiredTrait

internal class RequiredConstraint(val memberName: String, val trait: RequiredTrait, val pkgName: String, val writer: KotlinWriter) : AbstractConstraintTrait() {
    override fun render() {
        writer.write("require(data.$memberName != null) { #S }", "$memberName must be provided")
    }
}
