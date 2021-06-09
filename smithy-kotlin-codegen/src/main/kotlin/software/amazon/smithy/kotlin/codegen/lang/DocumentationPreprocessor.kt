/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.lang

import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.traits.DocumentationTrait
import software.amazon.smithy.model.transform.ModelTransformer

/**
 * Sanitize all instances of [DocumentationTrait]
 */
class DocumentationPreprocessor : KotlinIntegration {

    override fun preprocessModel(model: Model, settings: KotlinSettings): Model {
        val transformer = ModelTransformer.create()
        return transformer.mapTraits(model) { _, trait ->
            when (trait) {
                is DocumentationTrait -> {
                    val docs = sanitize(trait.value)
                    DocumentationTrait(docs, trait.sourceLocation)
                }
                else -> trait
            }
        }
    }

    // KDoc comments use inline markdown. Replace square brackets with escaped equivalents so that they
    // are not rendered as invalid links
    private fun sanitize(str: String): String = str.replace(Regex("(\\[)(.*)(\\])"), "&#91;$2&#93;")
}
