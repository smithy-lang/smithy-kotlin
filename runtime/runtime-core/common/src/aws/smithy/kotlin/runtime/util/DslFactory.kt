/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

/**
 * A factory type that can turn a [Builder] instance into a [Built] instance. Implementing this factory type can enable
 * usage of custom classes in DSL builders.
 */
public interface DslFactory<out Builder, out Built> {
    /**
     * Turns a [Builder] instance into a [Built] instance, first applying the given DSL block to the builder.
     * @param block A DSL block to apply to the builder.
     */
    public operator fun invoke(block: Builder.() -> Unit): Built
}
