package software.amazon.smithy.kotlin.codegen.service.constraints

import software.amazon.smithy.kotlin.codegen.core.GenerationContext
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.RequiredTrait
import kotlin.collections.iterator

/**
 * Generates validation code for request constraints on Smithy operation inputs.
 *
 * For a given [operation], this generator traverses the input structure and:
 * - Recursively inspects members of structures and lists.
 * - Applies trait-based validations (e.g., required, length, range).
 * - Generates Kotlin validation functions that check constraints at runtime.
 *
 * Output is written into a `<Operation>RequestConstraints.kt` file in the generated `constraints` package.
 */
internal class ConstraintGenerator(
    val ctx: GenerationContext,
    val operation: OperationShape,
    val delegator: KotlinDelegator,
) {
    val inputShape = ctx.model.expectShape(operation.input.get()) as StructureShape
    val inputMembers = inputShape.allMembers

    val opName = operation.id.name
    val pkgName = ctx.settings.pkg.name

    /**
     * Entry point for emitting validation code for the operation’s request type.
     * Delegates to [renderRequestConstraintsValidation].
     */
    fun render() {
        renderRequestConstraintsValidation()
    }

    /**
     * Recursively generates validation code for a given [memberShape].
     *
     * - If the target is a list, iterates over elements and validates them.
     * - If the target is a structure, recursively validates its members.
     * - For each trait (on the member or its target), invokes the matching trait generator.
     *   - `@required` traits are always enforced.
     *   - Other traits are wrapped in a null check before validation.
     */
    private fun generateConstraintValidations(prefix: String, memberShape: MemberShape, writer: KotlinWriter) {
        val targetShape = ctx.model.expectShape(memberShape.target)

        val memberName = memberShape.memberName
        val memberAndTargetTraits = memberShape.allTraits + targetShape.allTraits
        when {
            targetShape.isListShape ->
                for (member in targetShape.allMembers) {
                    val newMemberPrefix = "${targetShape.id.name}".replaceFirstChar { it.lowercase() }
                    writer.withBlock("if ($prefix$memberName != null) {", "}") {
                        withBlock("for ($newMemberPrefix${member.key} in $prefix$memberName ?: listOf()) {", "}") {
                            call { generateConstraintValidations(newMemberPrefix, member.value, writer) }
                        }
                    }
                }
            targetShape.isStructureShape ->
                for (member in targetShape.allMembers) {
                    val newMemberPrefix = "$prefix$memberName?."
                    generateConstraintValidations(newMemberPrefix, member.value, writer)
                }
        }
        for (memberTrait in memberAndTargetTraits.values) {
            val traitGenerator = getTraitGeneratorFromTrait(prefix, memberName, memberTrait, pkgName, writer)
            traitGenerator?.apply {
                if (memberTrait !is RequiredTrait) {
                    writer.withBlock("if ($prefix$memberName != null) {", "}") {
                        render()
                    }
                } else {
                    render()
                }
            }
        }
    }

    /**
     * Writes the top-level validation function for the operation’s input type.
     *
     * Inside, it calls [generateConstraintValidations] for each input member,
     * ensuring all modeled constraints are enforced.
     */
    private fun renderRequestConstraintsValidation() {
        delegator.useFileWriter("${opName}RequestConstraints.kt", "$pkgName.constraints") { writer ->
            val inputShape = ctx.model.expectShape(operation.input.get())
            val inputSymbol = ctx.symbolProvider.toSymbol(inputShape)

            writer.withBlock("public fun check${opName}RequestConstraint(data: #T) {", "}", inputSymbol) {
                for (memberShape in inputMembers.values) {
                    generateConstraintValidations("data.", memberShape, writer)
                }
            }
        }
    }
}
