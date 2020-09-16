/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde

/**
 * Metadata container for all fields of an object/class
 */
class SdkObjectDescriptor private constructor(builder: BuilderImpl) : SdkFieldDescriptor(
    builder.serialName ?: ANONYMOUS_OBJECT_NAME,
    SerialKind.Struct
) {
    val fields: List<SdkFieldDescriptor> = builder.fields

    companion object {
        // TODO: determine how to guard that reading this value from serialName results in error.
        // This value should never be read because JSON should never be looking for the name of a struct, etc,
        // and XML will always be setting serialName.
        const val ANONYMOUS_OBJECT_NAME: String = "ANONYMOUS_OBJECT"

        fun build(block: DslBuilder.() -> Unit): SdkObjectDescriptor = BuilderImpl().apply(block).build()
    }

    interface DslBuilder {
        /**
         * Declare a field belonging to this object
         */
        fun field(field: SdkFieldDescriptor)
        fun build(): SdkObjectDescriptor
        var serialName: String?
    }

    private class BuilderImpl : DslBuilder {
        val fields: MutableList<SdkFieldDescriptor> = mutableListOf()
        override var serialName: String? = null

        override fun field(field: SdkFieldDescriptor) {
            field.index = fields.size
            fields.add(field)
        }

        override fun build(): SdkObjectDescriptor = SdkObjectDescriptor(this)
    }
}
