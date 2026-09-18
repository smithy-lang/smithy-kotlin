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
     * A cached [FieldIndex] that provides O(1) lookup of a field's index by its serial name.
     */
    @InternalApi
    public val fieldIndex: FieldIndex
        get() = cachedFieldIndex ?: FieldIndex(fields).also { cachedFieldIndex = it }

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
) {
    // Serial name -> field index.
    private val byName: Map<String, Int> = buildMap(fields.size) {
        fields.forEach { field ->
            val serialName = field.serialName
            if (serialName !in this) put(serialName, field.index)
        }
    }

    // The inverse of byName, as an array for O(1) access: serial name of the field at each field index.
    private val serialNames: Array<String?> = run {
        val byIndex = byName.entries.associate { (serialName, index) -> index to serialName }
        Array(fields.size) { byIndex[it] }
    }

    /**
     * Resolves [serialName] to its field index (SdkFieldDescriptor.index), checking [expectedIndex]
     * first as an in-order guess before falling back to a hash lookup. Returns the field's index, or
     * [Deserializer.FieldIterator.UNKNOWN_FIELD] if no field has the given serial name.
     */
    public fun lookup(serialName: String, expectedIndex: Int): Int {
        if (expectedIndex < serialNames.size && serialNames[expectedIndex] == serialName) {
            return expectedIndex
        }
        return byName[serialName] ?: Deserializer.FieldIterator.UNKNOWN_FIELD
    }
}
