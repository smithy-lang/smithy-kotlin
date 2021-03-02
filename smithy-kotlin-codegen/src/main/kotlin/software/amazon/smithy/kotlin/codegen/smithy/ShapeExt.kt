/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.smithy

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId
import kotlin.streams.toList

inline fun <reified T : Shape> Model.shapes(): List<T> = shapes(T::class.java).toList()

/**
 * Extension function to return a shape of expected type.
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
inline fun <reified T : Shape> Model.expectShape(shapeId: ShapeId): T =
    expectShape(shapeId, T::class.java)

/**
 * Extension function to return a shape of expected type.
 */
inline fun <reified T : Shape> Model.expectShape(shapeId: String): T =
    this.expectShape(ShapeId.from(shapeId), T::class.java)
