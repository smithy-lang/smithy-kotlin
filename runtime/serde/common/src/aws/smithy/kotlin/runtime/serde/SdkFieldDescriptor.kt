/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde

import aws.smithy.kotlin.runtime.InternalApi

/**
 * This tag interface provides a mechanism to attach type-specific metadata to any field.
 * See [aws.smithy.kotlin.runtime.serde.xml.XmlList] for an example implementation.
 *
 * For example, to specify that a list should be serialized in XML such that values are wrapped
 * in a tag called "boo", pass an instance of XmlList to the FieldDescriptor of `XmlList(elementName="boo")`.
 */
@InternalApi
public interface FieldTrait

/**
 * Denotes that a Map or List may contain null values
 * Details at https://awslabs.github.io/smithy/1.0/spec/core/type-refinement-traits.html#sparse-trait
 */
@InternalApi
public object SparseValues : FieldTrait

/**
 * A protocol-agnostic type description of a field.
 */
@InternalApi
public sealed class SerialKind {
    /* ktlint-disable spacing-between-declarations-with-annotations */
    @InternalApi public object Unit : SerialKind()
    @InternalApi public object Integer : SerialKind()
    @InternalApi public object Long : SerialKind()
    @InternalApi public object Double : SerialKind()
    @InternalApi public object String : SerialKind()
    @InternalApi public object Boolean : SerialKind()
    @InternalApi public object Byte : SerialKind()
    @InternalApi public object Char : SerialKind()
    @InternalApi public object Short : SerialKind()
    @InternalApi public object Float : SerialKind()
    @InternalApi public object Enum : SerialKind()
    @InternalApi public object IntEnum : SerialKind()
    @InternalApi public object Map : SerialKind()
    @InternalApi public object List : SerialKind()
    @InternalApi public object Struct : SerialKind()
    @InternalApi public object Timestamp : SerialKind()
    @InternalApi public object Blob : SerialKind()
    @InternalApi public object Document : SerialKind()
    @InternalApi public object BigNumber : SerialKind()
    /* ktlint-enable spacing-between-declarations-with-annotations */

    override fun toString(): kotlin.String = this::class.simpleName ?: "SerialKind"
}

/**
 * Metadata to describe how a given member property maps to serialization.
 */
@InternalApi
public open class SdkFieldDescriptor(
    public val kind: SerialKind,
    public var index: Int = 0,
    public val traits: Set<FieldTrait> = emptySet(),
) {
    public constructor(kind: SerialKind, vararg trait: FieldTrait) : this(kind, 0, trait.toSet())
    public constructor(kind: SerialKind, traits: Set<FieldTrait>) : this(kind, 0, traits)

    // Reserved for format-specific companion extension functions
    @InternalApi
    public companion object;

    override fun toString(): String = "SdkFieldDescriptor.$kind(traits=${traits.joinToString(separator = ",") })"
}

/**
 * Returns the singleton instance of required Trait, or IllegalArgumentException if does not exist.
 */
@InternalApi
public inline fun <reified TExpected : FieldTrait> SdkFieldDescriptor.expectTrait(): TExpected {
    val x = traits.find { it::class == TExpected::class }
    requireNotNull(x) { "Expected to find trait ${TExpected::class} in $this but was not present." }

    return x as TExpected
}

@InternalApi
public inline fun <reified TExpected : FieldTrait> SdkFieldDescriptor.findTrait(): TExpected? {
    val x = traits.find { it::class == TExpected::class }

    return x as? TExpected
}

/**
 * Find a set of traits of the given type.
 */
@InternalApi
public inline fun <reified TExpected : FieldTrait> SdkFieldDescriptor.findTraits(): Set<TExpected> = traits
    .filter { it::class == TExpected::class }
    .map { it as TExpected }
    .toSet()

@InternalApi
public inline fun <reified TExpected : FieldTrait> SdkFieldDescriptor.hasTrait(): Boolean =
    traits.any { it is TExpected }
