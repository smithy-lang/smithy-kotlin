/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde

/**
 * Metadata container for all fields of an object/class
 */
class SdkObjectDescriptor private constructor(builder: BuilderImpl) : SdkFieldDescriptor(
    kind = SerialKind.Struct, traits = builder.traits
) {
    val fields: List<SdkFieldDescriptor> = builder.fields

    companion object {
        fun build(block: DslBuilder.() -> Unit): SdkObjectDescriptor = BuilderImpl().apply(block).build()
    }

    interface DslBuilder {
        /**
         * Declare a field belonging to this object
         */
        fun field(field: SdkFieldDescriptor)
        fun trait(trait: FieldTrait)
        fun build(): SdkObjectDescriptor
    }

    private class BuilderImpl : DslBuilder {
        val fields: MutableList<SdkFieldDescriptor> = mutableListOf()
        val traits: MutableSet<FieldTrait> = mutableSetOf()

        override fun field(field: SdkFieldDescriptor) {
            field.index = fields.size
            fields.add(field)
        }

        override fun trait(trait: FieldTrait) {
            traits.add(trait)
        }

        override fun build(): SdkObjectDescriptor = SdkObjectDescriptor(this)
    }
}
