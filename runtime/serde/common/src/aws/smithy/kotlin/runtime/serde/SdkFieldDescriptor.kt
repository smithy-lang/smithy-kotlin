/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde

/**
 * This tag interface provides a mechanism to attach type-specific metadata to any field.
 * See [aws.smithy.kotlin.runtime.serde.xml.XmlList] for an example implementation.
 *
 * For example, to specify that a list should be serialized in XML such that values are wrapped
 * in a tag called "boo", pass an instance of XmlList to the FieldDescriptor of `XmlList(elementName="boo")`.
 */
public interface FieldTrait

/**
 * Denotes that a Map or List may contain null values
 * Details at https://awslabs.github.io/smithy/1.0/spec/core/type-refinement-traits.html#sparse-trait
 */
public object SparseValues : FieldTrait

/**
 * A protocol-agnostic type description of a field.
 */
public sealed class SerialKind {
    public object Unit : SerialKind()
    public object Integer : SerialKind()
    public object Long : SerialKind()
    public object Double : SerialKind()
    public object String : SerialKind()
    public object Boolean : SerialKind()
    public object Byte : SerialKind()
    public object Char : SerialKind()
    public object Short : SerialKind()
    public object Float : SerialKind()
    public object Map : SerialKind()
    public object List : SerialKind()
    public object Struct : SerialKind()
    public object Timestamp : SerialKind()
    public object Blob : SerialKind()
    public object Document : SerialKind()
    public object BigNumber : SerialKind()

    override fun toString(): kotlin.String = this::class.simpleName ?: "SerialKind"
}

/**
 * Metadata to describe how a given member property maps to serialization.
 */
public open class SdkFieldDescriptor(
    public val kind: SerialKind,
    public var index: Int = 0,
    public val traits: Set<FieldTrait> = emptySet(),
) {
    public constructor(kind: SerialKind, vararg trait: FieldTrait) : this(kind, 0, trait.toSet())
    public constructor(kind: SerialKind, traits: Set<FieldTrait>) : this(kind, 0, traits)

    // Reserved for format-specific companion extension functions
    public companion object;

    override fun toString(): String = "SdkFieldDescriptor.$kind(traits=${traits.joinToString(separator = ",") })"
}

/**
 * Returns the singleton instance of required Trait, or IllegalArgumentException if does not exist.
 */
public inline fun <reified TExpected : FieldTrait> SdkFieldDescriptor.expectTrait(): TExpected {
    val x = traits.find { it::class == TExpected::class }
    requireNotNull(x) { "Expected to find trait ${TExpected::class} in $this but was not present." }

    return x as TExpected
}

public inline fun <reified TExpected : FieldTrait> SdkFieldDescriptor.findTrait(): TExpected? {
    val x = traits.find { it::class == TExpected::class }

    return x as? TExpected
}

/**
 * Find a set of traits of the given type.
 */
public inline fun <reified TExpected : FieldTrait> SdkFieldDescriptor.findTraits(): Set<TExpected> = traits
    .filter { it::class == TExpected::class }
    .map { it as TExpected }
    .toSet()

public inline fun <reified TExpected : FieldTrait> SdkFieldDescriptor.hasTrait(): Boolean =
    traits.any { it is TExpected }
