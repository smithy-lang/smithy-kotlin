package software.amazon.smithy.kotlin.codegen.service.contraints

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.model.traits.LengthTrait
import software.amazon.smithy.model.traits.PatternTrait
import software.amazon.smithy.model.traits.RangeTrait
import software.amazon.smithy.model.traits.RequiredTrait
import software.amazon.smithy.model.traits.Trait
import software.amazon.smithy.model.traits.UniqueItemsTrait

internal fun getTraitGeneratorFromTrait(
    memberPrefix: String,
    memberName: String,
    trait: Trait,
    pkgName: String,
    writer: KotlinWriter,
): AbstractConstraintTrait? = when (trait) {
    is LengthTrait -> LengthConstraint(memberPrefix, memberName, trait, pkgName, writer)
    is PatternTrait -> PatternConstraint(memberPrefix, memberName, trait, pkgName, writer)
    is RangeTrait -> RangeConstraint(memberPrefix, memberName, trait, pkgName, writer)
    is UniqueItemsTrait -> UniqueItemsConstraint(memberPrefix, memberName, trait, pkgName, writer)
    is RequiredTrait -> RequiredConstraint(memberPrefix, memberName, trait, pkgName, writer)
    else -> null
}
