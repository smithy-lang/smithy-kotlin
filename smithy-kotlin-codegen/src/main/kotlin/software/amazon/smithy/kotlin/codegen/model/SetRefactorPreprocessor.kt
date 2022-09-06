/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.model

import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.CollectionShape
import software.amazon.smithy.model.shapes.ListShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.traits.UniqueItemsTrait
import software.amazon.smithy.model.transform.ModelTransformer

/**
 * Replaces deprecated Smithy `set` shapes with `list` shapes annotated with the `@uniqueItems` trait. This transformer
 * should only be necessary until all models have migrated away from `set` shapes.
 */
class SetRefactorPreprocessor : KotlinIntegration {
    override fun preprocessModel(model: Model, settings: KotlinSettings): Model {
        val replaced = model
            .shapeIds
            .map(model::expectShape)
            .mapNotNull(::toSetShapeOrNull)
            .map(::toUniqueValuedList)

        return ModelTransformer.create().replaceShapes(model, replaced)
    }
}

// Necessary to suppress deprecation because we're detecting deprecated shapes!
@Suppress("DEPRECATION")
private fun toSetShapeOrNull(shape: Shape) = shape as? software.amazon.smithy.model.shapes.SetShape

private fun toUniqueValuedList(shape: CollectionShape): ListShape = ListShape
    .builder()
    .id(shape.id)
    .member(shape.member)
    .traits(shape.allTraits.values + UniqueItemsTrait())
    .build()
