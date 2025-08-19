package software.amazon.smithy.kotlin.codegen.service.constraints

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
): AbstractConstraintTraitGenerator? = when (trait) {
    is LengthTrait -> LengthConstraintGenerator(memberPrefix, memberName, trait, pkgName, writer)
    is PatternTrait -> PatternConstraintGenerator(memberPrefix, memberName, trait, pkgName, writer)
    is RangeTrait -> RangeConstraintGenerator(memberPrefix, memberName, trait, pkgName, writer)
    is UniqueItemsTrait -> UniqueItemsConstraintGenerator(memberPrefix, memberName, trait, pkgName, writer)
    is RequiredTrait -> RequiredConstraintGenerator(memberPrefix, memberName, trait, pkgName, writer)
    else -> null
}
