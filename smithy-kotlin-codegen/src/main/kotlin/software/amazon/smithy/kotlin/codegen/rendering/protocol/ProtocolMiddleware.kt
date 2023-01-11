/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.protocol

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.model.shapes.OperationShape

// TODO - we should probably rename this, it's not restricted to middleware really since render() allows you to do
// whatever you need/want to register and interact with an operation. This includes not being a middleware
// at all (e.g. HttpInterceptor). OperationCustomization?
/**
 * Interface that allows middleware to be registered and configured with the generated protocol client
 * How this interface is used is entirely protocol/generator dependent
 */
interface ProtocolMiddleware {
    // the name of the middleware
    val name: String

    /**
     * Gets the sort order for the middleware.
     *
     * Middleware are _registered_ according to this sort order. Lower values
     * are executed before higher values (for example, -128 comes before 0,
     * comes before 127). Customizations default to 0, which is the middle point
     * between the minimum and maximum order values.
     *
     * @return Returns the sort order, defaulting to 0.
     */
    val order: Byte
        get() = 0

    /**
     * Render the registration of this middleware into an operation
     */
    fun render(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter)

    /**
     * Test if this middleware applies to a given operation.
     */
    fun isEnabledFor(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Boolean = true

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
