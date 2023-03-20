/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.core

/**
 * Container for formatting a (Kotlin) expression. This is used to delay formatting to a writer which is
 * useful when a writer isn't available yet or needs to do additional formatting using the formatted
 * result (e.g. formatting into a container).
 */
data class RenderExpr(
    val format: String,
    val args: List<Any> = emptyList(),
)

/**
 * Convenience vararg init for [RenderExpr]
 */
fun RenderExpr(format: String, vararg args: Any): RenderExpr = RenderExpr(format, args.toList())