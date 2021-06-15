/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde

/**
 * This tag interface provides a mechanism to attach type-specific metadata to any field.
 * See [software.aws.clientrt.serde.xml.XmlList] for an example implementation.
 *
 * For example, to specify that a list should be serialized in XML such that values are wrapped
 * in a tag called "boo", pass an instance of XmlList to the FieldDescriptor of `XmlList(elementName="boo")`.
 */
interface FieldTrait

/**
 * Denotes that a Map or List may contain null values
 * Details at https://awslabs.github.io/smithy/1.0/spec/core/type-refinement-traits.html#sparse-trait
 */
object SparseValues : FieldTrait

/**
 * A protocol-agnostic type description of a field.
 */
sealed class SerialKind {
    object Unit : SerialKind()
    object Integer : SerialKind()
    object Long : SerialKind()
    object Double : SerialKind()
    object String : SerialKind()
    object Boolean : SerialKind()
    object Byte : SerialKind()
    object Char : SerialKind()
    object Short : SerialKind()
    object Float : SerialKind()
    object Map : SerialKind()
    object List : SerialKind()
    object Struct : SerialKind()
    object Timestamp : SerialKind()
    object Blob : SerialKind()
    object Document : SerialKind()
    object BigNumber : SerialKind()

    override fun toString(): kotlin.String = this::class.simpleName ?: "SerialKind"
}

/**
 * Metadata to describe how a given member property maps to serialization.
 */
open class SdkFieldDescriptor(val kind: SerialKind, var index: Int = 0, val traits: Set<FieldTrait> = emptySet()) {
    constructor(kind: SerialKind, vararg trait: FieldTrait) : this(kind, 0, trait.toSet())
    constructor(kind: SerialKind, traits: Set<FieldTrait>) : this(kind, 0, traits)

    // Reserved for format-specific companion extension functions
    companion object;

    override fun toString(): String = "SdkFieldDescriptor.$kind(traits=${traits.joinToString(separator = ",") })"
}

/**
 * Returns the singleton instance of required Trait, or IllegalArgumentException if does not exist.
 */
inline fun <reified TExpected : FieldTrait> SdkFieldDescriptor.expectTrait(): TExpected {
    val x = traits.find { it::class == TExpected::class }
    requireNotNull(x) { "Expected to find trait ${TExpected::class} in $this but was not present." }

    return x as TExpected
}

inline fun <reified TExpected : FieldTrait> SdkFieldDescriptor.findTrait(): TExpected? {
    val x = traits.find { it::class == TExpected::class }

    return x as? TExpected
}

/**
 * Find a set of traits of the given type.
 */
inline fun <reified TExpected : FieldTrait> SdkFieldDescriptor.findTraits(): Set<TExpected> = traits
    .filter { it::class == TExpected::class }
    .map { it as TExpected }
    .toSet()

inline fun <reified TExpected : FieldTrait> SdkFieldDescriptor.hasTrait() = traits.any { it is TExpected }
