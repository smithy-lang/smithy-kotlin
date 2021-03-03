/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.traits

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.AbstractTrait
import software.amazon.smithy.model.traits.AbstractTraitBuilder
import software.amazon.smithy.utils.SmithyBuilder
import software.amazon.smithy.utils.ToSmithyBuilder

/**
 * The namespace synthetic shapes are cloned into
 */
const val SYNTHETIC_NAMESPACE: String = "smithy.kotlin.synthetic"

/**
 * Defines a shape as being a clone of another modeled shape.
 *
 *
 * Must only be used as a runtime trait-only applied to shapes based on model processing
 */
class SyntheticClone private constructor(builder: Builder) :
    AbstractTrait(ID, builder.sourceLocation), ToSmithyBuilder<SyntheticClone> {
    /**
     * The original shape ID cloned from
     */
    val archetype: ShapeId = requireNotNull(builder.archetype) { "Original ShapeId is required for SyntheticClone trait" }

    override fun createNode(): Node {
        throw CodegenException("attempted to serialize runtime only trait")
    }

    override fun toBuilder(): SmithyBuilder<SyntheticClone> {
        val builder = Builder()
        builder.archetype = archetype
        return builder
    }

    /**
     * Builder for [SyntheticClone].
     */
    class Builder internal constructor() : AbstractTraitBuilder<SyntheticClone, Builder>() {
        var archetype: ShapeId? = null

        override fun build(): SyntheticClone {
            return SyntheticClone(this)
        }
    }

    companion object {
        val ID = ShapeId.from("smithy.kotlin.traits#syntheticClone")

        fun build(block: SyntheticClone.Builder.() -> Unit): SyntheticClone = Builder().apply(block).build()
    }
}
