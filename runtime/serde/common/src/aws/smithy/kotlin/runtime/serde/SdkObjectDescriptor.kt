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
     *
     * Descriptors are hoisted to file-scope singletons by codegen, so a single descriptor may be read
     * concurrently by multiple threads deserializing the same struct type. The unsynchronized cache
     * below is safe under that access: [FieldIndex] is effectively immutable (its state lives in `val`
     * fields fully populated by its constructor, so it is safely published even through the data race),
     * and construction is idempotent — the only cost of a race is building the index more than once.
     *
     * [serialNameOf] must be stable for a given descriptor. That holds because a descriptor is produced
     * for exactly one protocol, so only that protocol's `serialName` accessor is ever passed here.
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
    // Aligned by list position, NOT by SdkFieldDescriptor.index: `index` is a mutable var that
    // Builder.field() reassigns, so a field-descriptor instance shared across multiple object
    // descriptors can carry an index outside this descriptor's 0..size-1 range. List positions are
    // always dense and in-bounds, so they are safe to use for the in-order guess arrays.
    private val serialNames: Array<String?> = arrayOfNulls(fields.size)

    // Field index (SdkFieldDescriptor.index) at each list position — the value returned to callers.
    private val fieldIndices: IntArray = IntArray(fields.size)

    // Serial name -> field index, for O(1) fallback when the in-order guess misses.
    private val byName: MutableMap<String, Int> = HashMap(fields.size)

    init {
        for (position in fields.indices) {
            val field = fields[position]
            val name = serialNameOf(field)
            serialNames[position] = name
            fieldIndices[position] = field.index
            // Keep the FIRST field for a duplicated serial name, matching the old linear scan.
            if (name !in byName) byName[name] = field.index
        }
    }

    /**
     * Resolves [serialName] to its field index (SdkFieldDescriptor.index), checking the field at list
     * position [expectedPosition] first as an in-order guess before falling back to a hash lookup.
     * Returns the field's index, or [Deserializer.FieldIterator.UNKNOWN_FIELD] if no field has the
     * given serial name.
     */
    public fun lookup(serialName: String, expectedPosition: Int): Int {
        if (expectedPosition < serialNames.size && serialNames[expectedPosition] == serialName) {
            return fieldIndices[expectedPosition]
        }
        return byName[serialName] ?: Deserializer.FieldIterator.UNKNOWN_FIELD
    }
}
