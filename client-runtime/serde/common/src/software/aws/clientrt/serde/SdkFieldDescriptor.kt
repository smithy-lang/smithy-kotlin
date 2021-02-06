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

// Denotes that a Map or List may contain null values
object SparseValues: FieldTrait

/**
 * A protocol-agnostic type description of a field.
 * @param container specifies if the kind can hold values or is just a value itself
 */
sealed class SerialKind(val container: kotlin.Boolean) {
    object Unit : SerialKind(false)
    object Integer : SerialKind(false)
    object Long : SerialKind(false)
    object Double : SerialKind(false)
    object String : SerialKind(false)
    object Boolean : SerialKind(false)
    object Byte : SerialKind(false)
    object Char : SerialKind(false)
    object Short : SerialKind(false)
    object Float : SerialKind(false)
    object Map : SerialKind(true)
    object List : SerialKind(true)
    object Struct : SerialKind(true)
    object Timestamp : SerialKind(false)
    object Blob : SerialKind(false)
    object Document : SerialKind(false)
    object BigNumber : SerialKind(false)

    override fun toString(): kotlin.String {
        return this::class.simpleName ?: "SerialKind"
    }
}


/**
 * Metadata to describe how a given member property maps to serialization.
 */
open class SdkFieldDescriptor(val kind: SerialKind, var index: Int = 0, val traits: Set<FieldTrait> = emptySet()) {
    constructor(kind: SerialKind, vararg trait: FieldTrait): this(kind, 0, trait.toSet())
    constructor(kind: SerialKind, traits: Set<FieldTrait>): this(kind, 0, traits)

    // Reserved for format-specific companion extension functions
    companion object;

    /**
     * Returns the singleton instance of required Trait, or IllegalArgumentException if does not exist.
     */
    inline fun <reified TExpected : FieldTrait> expectTrait(): TExpected {
        val x = traits.find { it::class == TExpected::class }
        requireNotNull(x) { "Expected to find trait ${TExpected::class} in $this but was not present." }

        return x as TExpected
    }

    inline fun <reified TExpected : FieldTrait> findTrait(): TExpected? {
        val x = traits.find { it::class == TExpected::class }

        return x as TExpected?
    }

    inline fun <reified TExpected : FieldTrait> hasTrait() = traits.any { it is TExpected  }

    override fun toString(): String {
        return "SdkFieldDescriptor.$kind(traits=${traits.joinToString(separator = ",") })"
    }
}
