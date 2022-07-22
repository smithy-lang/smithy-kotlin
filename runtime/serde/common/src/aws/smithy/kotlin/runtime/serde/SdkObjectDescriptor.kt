/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde

/**
 * Metadata container for all fields of an object/class
 */
public class SdkObjectDescriptor private constructor(builder: Builder) : SdkFieldDescriptor(
    kind = SerialKind.Struct, traits = builder.traits
) {
    public val fields: List<SdkFieldDescriptor> = builder.fields

    public companion object {
        public inline fun build(block: Builder.() -> Unit): SdkObjectDescriptor = Builder().apply(block).build()
    }

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

        @PublishedApi
        internal fun build(): SdkObjectDescriptor = SdkObjectDescriptor(this)
    }
}
