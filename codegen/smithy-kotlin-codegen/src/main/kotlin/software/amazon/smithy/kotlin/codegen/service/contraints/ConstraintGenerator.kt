package software.amazon.smithy.kotlin.codegen.service.contraints

import software.amazon.smithy.kotlin.codegen.core.GenerationContext
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.StructureShape

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
        renderInputConstraintsValidation()
        renderOutputConstraintsValidation()
    }

    private fun renderInputConstraintsValidation() {
        delegator.useFileWriter("${opName}InputConstraints.kt", "$pkgName.constraints") { writer ->
            writer.addImport("$pkgName.model", "${operation.id.name}Request")

            writer.withBlock("public fun check${opName}InputConstraint(data: ${opName}Request) {", "}") {
                for (parameterShape in inputMembers.values) {
                    val parameterName = parameterShape.memberName
                    val parameterTraits = parameterShape.allTraits
                    for (parameterTraitMap in parameterTraits) {
//                        val parameterShapeId = parameterTraitMap.key
                        val parameterTrait = parameterTraitMap.value
                        val traitGenerator = getTraitGeneratorFromTrait(parameterName, parameterTrait, pkgName, writer)
                        traitGenerator?.render()
                    }
                }
            }
        }
    }

    private fun renderOutputConstraintsValidation() {
        delegator.useFileWriter("${opName}OutputConstraints.kt", "$pkgName.constraints") { writer ->
            writer.addImport("$pkgName.model", "${operation.id.name}Response")

            writer.withBlock("public fun check${opName}OutputConstraint(data: ${opName}Response) {", "}") {
                for (parameterShape in outputMembers.values) {
                    val parameterTraits = parameterShape.allTraits
                    for (parameterTraitMap in parameterTraits) {
                        val parameterShapeId = parameterTraitMap.key
                        val parameterTrait = parameterTraitMap.value
                        val traitGenerator = getTraitGeneratorFromTrait(parameterShapeId.name, parameterTrait, pkgName, writer)
                        traitGenerator?.render()
                    }
                }
            }
        }
    }
}
