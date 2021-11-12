/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde

/**
 * Metadata container for all fields of an object/class
 */
class SdkObjectDescriptor private constructor(builder: Builder) : SdkFieldDescriptor(
    kind = SerialKind.Struct, traits = builder.traits
) {
    val fields: List<SdkFieldDescriptor> = builder.fields

    companion object {
        inline fun build(block: Builder.() -> Unit): SdkObjectDescriptor = Builder().apply(block).build()
    }

    class Builder {
        internal val fields: MutableList<SdkFieldDescriptor> = mutableListOf()
        internal val traits: MutableSet<FieldTrait> = mutableSetOf()

        fun field(field: SdkFieldDescriptor) {
            field.index = fields.size
            fields.add(field)
        }

        fun trait(trait: FieldTrait) {
            traits.add(trait)
        }

        @PublishedApi
        internal fun build(): SdkObjectDescriptor = SdkObjectDescriptor(this)
    }
}
