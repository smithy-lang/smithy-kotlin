/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.waiters

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.core.defaultName
import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.waiters.Waiter

/**
 * Holds context information about a waiter.
 * @param ctx The [CodegenContext] in which code will be generated.
 * @param service The [ServiceShape] for this waiter's operation.
 * @param op The [OperationShape] for this waiter.
 * @param name The modeled name of this waiter.
 * @param waiter The Smithy [Waiter] structure for this waiter.
 */
data class WaiterInfo(
    val ctx: CodegenContext,
    val service: ServiceShape,
    val op: OperationShape,
    val name: String,
    val waiter: Waiter,
) {
    /**
     * The [StructureShape] of the input for this waiter's operation.
     */
    val input: StructureShape = ctx.model.expectShape<StructureShape>(op.input.get())

    /**
     * The [Symbol] of the input for this waiter's operation.
     */
    val inputSymbol: Symbol = ctx.symbolProvider.toSymbol(input)

    /**
     * The method name to use for this waiter in code generation.
     */
    val methodName: String = run {
        val baseName = when {
            name.startsWith("wait", ignoreCase = true) -> name
            else -> "waitUntil$name"
        }
        baseName.replaceFirstChar(Char::lowercaseChar)
    }

    /**
     * The method name of the waiter's operation.
     */
    val opMethodName: String = op.defaultName()

    /**
     * The [StructureShape] of the output for this waiter's operation.
     */
    val output: StructureShape = ctx.model.expectShape<StructureShape>(op.output.get())

    /**
     * The [Symbol] of the output for this waiter's operation.
     */
    val outputSymbol: Symbol = ctx.symbolProvider.toSymbol(output)

    /**
     * The [Symbol] for this waiter's service.
     */
    val serviceSymbol: Symbol = ctx.symbolProvider.toSymbol(service)
}
