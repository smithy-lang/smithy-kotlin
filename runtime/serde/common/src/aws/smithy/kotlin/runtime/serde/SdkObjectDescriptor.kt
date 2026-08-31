/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde

import aws.smithy.kotlin.runtime.InternalApi
import kotlin.concurrent.Volatile

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

    // Racy single-check: concurrent callers may each build an index, but FieldIndex is deeply immutable
    // and value-equivalent, so losing the race only wastes an instance.
    @Volatile
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
    // Indexed by list position (always dense, in-bounds), NOT SdkFieldDescriptor.index, which is a
    // mutable var and can fall outside 0..size-1 when a field is shared across object descriptors.
    private val serialNames: Array<String> = Array(fields.size) { serialNameOf(fields[it]) }

    // Field index at each list position — the value returned to callers.
    private val fieldIndices: IntArray = IntArray(fields.size) { fields[it].index }

    // Serial name -> field index, for O(1) fallback when the in-order guess misses.
    private val byName: Map<String, Int> = buildMap(fields.size) {
        for (position in fields.indices) {
            // Keep the FIRST field for a duplicated serial name, matching the old linear scan.
            if (serialNames[position] !in this) put(serialNames[position], fieldIndices[position])
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
