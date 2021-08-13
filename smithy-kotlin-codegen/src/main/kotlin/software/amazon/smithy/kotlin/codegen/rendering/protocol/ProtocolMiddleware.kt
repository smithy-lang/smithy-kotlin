/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.protocol

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.model.shapes.OperationShape

/**
 * Interface that allows middleware to be registered and configured with the generated protocol client
 * How this interface is used is entirely protocol/generator dependent
 */
interface ProtocolMiddleware {
    // the name of the middleware to install
    val name: String

    // flag that controls whether renderConfigure() needs called
    val needsConfiguration: Boolean
        get() = true

    /**
     * Test if this middleware applies to a given operation.
     */
    fun isEnabledFor(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Boolean = true

    /**
     * Register any imports or dependencies that will be needed to use this middleware at runtime
     */
    fun addImportsAndDependencies(writer: KotlinWriter) {}

    /**
     * Render the body of the install step which configures this middleware. Implementations do not need to open
     * the surrounding block.
     *
     * Example
     * ```
     * install(MyFeature) {
     *     // this is the renderConfigure() entry point
     * }
     * ```
     */
    fun renderConfigure(writer: KotlinWriter) {}

    /**
     * Render any internal static properties re-used by the feature
     */
    fun renderProperties(writer: KotlinWriter) {}
}

/**
 * Convenience function to replace one middleware with another.
 * Adapted from https://discuss.kotlinlang.org/t/best-way-to-replace-an-element-of-an-immutable-list/8646/9
 */
fun <T : ProtocolMiddleware> List<T>.replace(newValue: T, block: (T) -> Boolean) = map {
    if (block(it)) newValue else it
}
