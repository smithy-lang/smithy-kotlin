/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde

import aws.smithy.kotlin.runtime.InternalApi

/**
 * Metadata container for all fields of an object/class
 */
@InternalApi
public class SdkObjectDescriptor private constructor(builder: Builder) :
    SdkFieldDescriptor(
        kind = SerialKind.Struct,
        traits = builder.traits,
    ) {
    public val fields: List<SdkFieldDescriptor> = builder.fields

    private var cachedFieldIndex: FieldIndex? = null

    /**
     * Returns a cached [FieldIndex] that provides O(1) lookup of a field's index by its serial name.
     */
    @InternalApi
    public fun fieldIndex(serialNameOf: (SdkFieldDescriptor) -> String): FieldIndex = cachedFieldIndex ?: FieldIndex(fields, serialNameOf).also { cachedFieldIndex = it }

    @InternalApi
    public companion object {
        public inline fun build(block: Builder.() -> Unit): SdkObjectDescriptor = Builder().apply(block).build()
    }

    @InternalApi
    public class Builder {
        internal val fields: MutableList<SdkFieldDescriptor> = mutableListOf()
        internal val traits: MutableSet<FieldTrait> = mutableSetOf()

        public fun field(field: SdkFieldDescriptor) {
            field.index = fields.size
            fields.add(field)
        }

        public fun trait(trait: FieldTrait) {
            traits.add(trait)
        }

        @InternalApi
        public fun build(): SdkObjectDescriptor = SdkObjectDescriptor(this)
    }
}

/**
 * A precomputed index over a struct's fields that maps serial names to field indices.
 */
@InternalApi
public class FieldIndex internal constructor(
    fields: List<SdkFieldDescriptor>,
    serialNameOf: (SdkFieldDescriptor) -> String,
) {
    // Serial names aligned by field index (fields[i].index == i), enabling a cheap in-order guess.
    private val serialNames: Array<String?> = arrayOfNulls(fields.size)

    // Serial name -> field index, for O(1) fallback when the in-order guess misses.
    private val byName: MutableMap<String, Int> = HashMap(fields.size)

    init {
        for (field in fields) {
            val name = serialNameOf(field)
            serialNames[field.index] = name
            byName[name] = field.index
        }
    }

    /**
     * Resolves [serialName] to its field index, checking [expectedIndex] first as an in-order guess
     * before falling back to a hash lookup. Returns the field's index, or
     * [Deserializer.FieldIterator.UNKNOWN_FIELD] if no field has the given serial name.
     */
    public fun lookup(serialName: String, expectedIndex: Int): Int {
        if (expectedIndex < serialNames.size && serialNames[expectedIndex] == serialName) {
            return expectedIndex
        }
        return byName[serialName] ?: Deserializer.FieldIterator.UNKNOWN_FIELD
    }
}
