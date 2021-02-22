/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.lang

import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.clientName
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.isEnum
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.transform.ModelTransformer
import java.util.logging.Logger

/**
 * Integration that pre-processes the model and renames builtin types
 *
 * e.g. if a model has `com.foo#Unit` defined it would conflict with the builtin `kotlin.Unit`.
 * This would force customers to alias symbols to disambiguate which is not a good experience.
 *
 * Instead we will rename them by prefixing them with the service name (e.g. `FooUnit`).
 */
class BuiltinPreprocessor : KotlinIntegration {

    private val logger = Logger.getLogger(javaClass.name)

    override fun preprocessModel(model: Model, settings: KotlinSettings): Model {
        val transformer = ModelTransformer.create()
        val renamed = getRenamed(model, settings)
        return transformer.renameShapes(model, renamed)
    }

    private fun getRenamed(model: Model, settings: KotlinSettings): Map<ShapeId, ShapeId> {
        val serviceNamespace = settings.getService(model).id.namespace
        val kotlinBuiltins = KotlinTypes.All.map { it.name }.toSet()

        return model.shapeIds
            .filter {
                // filter out symbols not defined in this model (i.e. don't rename `smithy.api#String`),
                // filter members, we only care about top-level shapes
                it.namespace == serviceNamespace && it.member.isEmpty && (it.name in kotlinBuiltins)
            }.filter {
                /*
                further filter to only top-level aggregate shapes where we will actually generate a new type/symbol

                models often define things like:

                ```
                namespace com.foo

                string FooString

                structure {
                    foo: FooString
                }
                ```

                `FooString` is fine here though and need not be renamed as no type is
                generated for it (it will end up as `kotlin.String`).
                */
                val shape = model.expectShape(it)
                shape.isStructureShape || shape.isUnionShape || shape.isEnum
            }.associate {
                it to prefixWithService(settings.sdkId, it)
            }.also {
                it.forEach { entry ->
                    logger.warning("renaming shape `${entry.key}` to `${entry.value}`")
                }
            }
    }
}

private fun prefixWithService(sdkId: String, id: ShapeId): ShapeId {
    val prefixed = "${sdkId.clientName()}${id.name}"
    return ShapeId.fromParts(id.namespace, prefixed)
}
