/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.util

/**
 * Descriptor for how a configuration property is rendered when the configuration is built
 */
sealed class ConfigPropertyType {
    companion object {
        val DoNotRender = Custom(render = { _, _ -> }, renderBuilder = { _, _ -> })
    }

    /**
     * A property type that uses the symbol type and builder symbol directly
     */
    object SymbolDefault : ConfigPropertyType()

    /**
     * Specifies that the value should be populated with a constant value that cannot be overridden in the builder.
     * These are effectively read-only properties that will show up in the configuration type but not the builder.
     *
     * @param value the value to assign to the property at construction time
     */
    data class ConstantValue(val value: String) : ConfigPropertyType()

    /**
     * A configuration property that is required to be set (i.e. not null).
     * If the property is not provided in the builder then an IllegalArgumentException is thrown
     *
     * @param message The exception message to throw if the property is null, if not set a message is generated
     * automatically based on the property name
     */
    data class Required(val message: String? = null) : ConfigPropertyType()

    /**
     * A configuration property that is required but has a default value. This has the same semantics of [Required]
     * but instead of an exception the default value will be used when not provided in the builder.
     *
     * @param default the value to assign if the corresponding builder property is null
     */
    data class RequiredWithDefault(val default: String) : ConfigPropertyType()

    /**
     * A configuration property that uses [render] to manually render the actual immutable property.
     * @param renderBuilder used to render the builder property if set. By default, builder properties are all
     * rendered using [KotlinPropertyFormatter] with the symbol default.
     *
     * e.g. `var propertyName: Symbol[?] = SymbolDefault`
     */
    data class Custom(
        val render: CustomPropertyRenderer,
        val renderBuilder: CustomPropertyRenderer? = null,
    ) : ConfigPropertyType()
}
