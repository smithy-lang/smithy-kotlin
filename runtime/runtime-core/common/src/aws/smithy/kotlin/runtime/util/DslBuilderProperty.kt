/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.InternalApi

/**
 * Encapsulates a mutable property for a complex (i.e., non-scalar) type meant to be modified in a mutable builder
 * implementation. This single property provides an [instance] field for setting a literal value and two [dsl] overloads
 * for providing configuration for a dynamically-constructed instance. Note that callers should not _read_ the value of
 * [instance] and should instead use the [supply] function.
 *
 * @param SuperBuilder The topmost class/interface for valid builder instances for this property. Any subtype builders
 * must inherit/implement this type. Instances of [SuperBuilder] should build to a [SuperBuilt] instance.
 * @param SuperBuilt The topmost class/interface for valid _built_ instances of this property. Any subtypes must
 * inherit/implement this type.
 * @param defaultFactory The default factory type to use for constructing new instances. This default type will be used
 * if no specific instance has been set and no [dsl] invocation has specified a different factory.
 * @param toBuilderApplicator A method that can turn a built instance (i.e., of [SuperBuilt]) into an applicator
 * function that applies the built properties to a builder instance.
 * @param managedTransform An optional transformation that will apply to instances built from [dsl] but not instances
 * set directly on [instance]. This is useful for example when applying management wrappers around types whose lifetime
 * will be managed by the runtime, not by callers.
 */
@InternalApi
public class DslBuilderProperty<SuperBuilder, SuperBuilt>(
    private val defaultFactory: DslFactory<SuperBuilder, SuperBuilt>,
    private val toBuilderApplicator: SuperBuilt.() -> (SuperBuilder.() -> Unit),
    private val managedTransform: (SuperBuilt) -> SuperBuilt = { it },
) {
    private var configApplicator: SuperBuilder.() -> Unit = { }
    private var factory: DslFactory<SuperBuilder, SuperBuilt> = defaultFactory
    private var state = SupplierState.NOT_INITIALIZED

    /**
     * Yields the final instance of this property, either directly from [instance] or as built given the factory and
     * DSL block passed to [dsl].
     */
    public var supply: () -> SuperBuilt = { managedTransform(factory { }) }
        private set

    /**
     * Sets the value of this property to a literal instance. Note that callers should not _read_ the value of
     * [instance] and should instead use the [supply] function.
     */
    public var instance: SuperBuilt? = null
        set(value) {
            state = when (state) {
                SupplierState.NOT_INITIALIZED -> SupplierState.INITIALIZED
                else -> SupplierState.EXPLICIT_INSTANCE
            }

            field = value
            supply = when (value) {
                null -> {
                    // Reset factory back to default
                    factory = defaultFactory
                    { managedTransform(factory { }) }
                }
                else -> { { value } }
            }

            configApplicator = value?.toBuilderApplicator() ?: { }
        }

    /**
     * Configure a builder for this property value.
     * @param SubBuilder The subtype of the [SuperBuilder] type which describes the specific type being used for the DSL
     * block.
     * @param SubBuilt The subtype of the [SuperBuilt] type which describes the specific type of instance which will be
     * built.
     * @param factory The factory which can turn a builder into a built instance. The specific [SubBuilder] type of this
     * factory will be used in the DSL block.
     * @param block A DSL block which is applied to the builder instance before being built.
     */
    public fun <SubBuilder : SuperBuilder, SubBuilt : SuperBuilt> dsl(
        factory: DslFactory<SubBuilder, SubBuilt>,
        block: SubBuilder.() -> Unit,
    ) {
        when (state) {
            SupplierState.EXPLICIT_INSTANCE -> throw ClientException("An explicit instance is already configured and its configuration cannot be modified")
            else -> state = SupplierState.EXPLICIT_CONFIG
        }

        this.factory = factory

        val previousApplicator = configApplicator
        configApplicator = {
            previousApplicator()

            try {
                // This should be safe to cast if no one tried to change the type from before.
                @Suppress("UNCHECKED_CAST")
                this as SubBuilder
            } catch (e: ClassCastException) {
                // Otherwise, throw an exception!
                throw ClientException(
                    "Cannot change DSL property type after initially setting it to ${this!!::class.simpleName}",
                    e,
                )
            }

            block(this)
        }
        supply = { managedTransform(factory(configApplicator)) }
    }
}

private enum class SupplierState {
    NOT_INITIALIZED,
    INITIALIZED,
    EXPLICIT_CONFIG,
    EXPLICIT_INSTANCE,
}
