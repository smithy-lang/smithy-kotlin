package software.amazon.smithy.kotlin.codegen.service.contraints

import software.amazon.smithy.kotlin.codegen.core.GenerationContext
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.StructureShape
import kotlin.collections.iterator

internal class ConstraintGenerator(
    val ctx: GenerationContext,
    val operation: OperationShape,
    val delegator: KotlinDelegator,
) {
    val inputShape = ctx.model.expectShape(operation.input.get()) as StructureShape
    val inputMembers = inputShape.allMembers
    val outputShape = ctx.model.expectShape(operation.output.get()) as StructureShape
    val outputMembers = outputShape.allMembers

    val opName = operation.id.name
    val pkgName = ctx.settings.pkg.name

    fun render() {
        renderRequestConstraintsValidation()
        renderResponseConstraintsValidation()
    }
    private fun addConstaintValidation(prefix: String, memberShape: MemberShape, writer: KotlinWriter) {
        val targetShape = ctx.model.expectShape(memberShape.target)

        val memberName = memberShape.memberName
        val memberTraits = memberShape.allTraits + targetShape.allTraits

        for (memberTraitMap in memberTraits) {
            val memberTrait = memberTraitMap.value
            val traitGenerator = getTraitGeneratorFromTrait(prefix, memberName, memberTrait, pkgName, writer)
            traitGenerator?.render()
        }

        for (member in targetShape.allMembers) {
            val newMemberPrefix = "${targetShape.id.name}".replaceFirstChar { it.lowercase() }
            writer.withBlock("if (${prefix}$memberName != null) {", "}") {
                withBlock("for (${newMemberPrefix}${member.key} in ${prefix}$memberName) {", "}") {
                    call { addConstaintValidation(newMemberPrefix, member.value, writer) }
                }
            }
        }
    }

    private fun renderRequestConstraintsValidation() {
        delegator.useFileWriter("${opName}RequestConstraints.kt", "$pkgName.constraints") { writer ->
            writer.addImport("$pkgName.model", "${operation.id.name}Request")

            writer.withBlock("public fun check${opName}RequestConstraint(data: ${opName}Request) {", "}") {
                for (memberShape in inputMembers.values) {
                    addConstaintValidation("data.", memberShape, writer)
                }
            }
        }
    }

    private fun renderResponseConstraintsValidation() {
        delegator.useFileWriter("${opName}ResponseConstraints.kt", "$pkgName.constraints") { writer ->
            writer.addImport("$pkgName.model", "${operation.id.name}Response")

            writer.withBlock("public fun check${opName}ResponseConstraint(data: ${opName}Response) {", "}") {
                for (memberShape in outputMembers.values) {
                    addConstaintValidation("data.", memberShape, writer)
                }
            }
        }
    }
}
