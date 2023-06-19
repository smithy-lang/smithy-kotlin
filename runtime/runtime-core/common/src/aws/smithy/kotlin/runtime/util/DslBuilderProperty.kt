/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.ClientException

public class DslBuilderProperty<SuperBuilder, SuperBuilt>(
    private val defaultFactory: DslFactory<SuperBuilder, SuperBuilt>,
    private val toBuilderApplicator: SuperBuilt.() -> (SuperBuilder.() -> Unit),
    private val managedTransform: (SuperBuilt) -> SuperBuilt = { it },
) {
    private var configApplicator: SuperBuilder.() -> Unit = { }
    private var factory: DslFactory<SuperBuilder, SuperBuilt> = defaultFactory
    private var state = SupplierState.NOT_INITIALIZED

    public var supply: () -> SuperBuilt = { managedTransform(factory { }) }
        private set

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

            @Suppress("UNCHECKED_CAST") // This is safe because [factory] is definitely the right type
            block(this as SubBuilder)
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

public interface DslFactory<out Builder, out Built> {
    public operator fun invoke(block: Builder.() -> Unit): Built
}
