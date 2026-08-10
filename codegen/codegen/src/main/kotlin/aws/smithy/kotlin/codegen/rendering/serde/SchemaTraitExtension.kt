/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.serde

import aws.smithy.kotlin.codegen.core.InlineKotlinWriter
import aws.smithy.kotlin.codegen.core.RuntimeTypes.Serde.Schema
import software.amazon.smithy.aws.traits.protocols.AwsQueryErrorTrait
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.HttpHeaderTrait
import software.amazon.smithy.model.traits.HttpPrefixHeadersTrait
import software.amazon.smithy.model.traits.HttpQueryTrait
import software.amazon.smithy.model.traits.JsonNameTrait
import software.amazon.smithy.model.traits.MediaTypeTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.model.traits.Trait
import software.amazon.smithy.model.traits.XmlNameTrait
import software.amazon.smithy.model.traits.XmlNamespaceTrait
import software.amazon.smithy.rulesengine.traits.ContextParamTrait

/**
 * Renders a Smithy model [Trait] into the runtime-trait construction emitted in a generated schema.
 */
public fun interface TraitRenderer {
    public fun render(trait: Trait): InlineKotlinWriter
}

/**
 * Registry of [TraitRenderer]s keyed by trait [ShapeId], populated by the schema generator with first-party
 * (AWS SDK / Smithy) renderers and augmentable by downstream integrations.
 */
public class SchemaTraitExtension internal constructor(
    private val renderers: MutableMap<ShapeId, TraitRenderer>,
    private val firstPartyIds: Set<ShapeId>,
) {
    /** Register (or override) the [renderer] for the trait identified by [traitShapeId]. */
    public fun add(traitShapeId: ShapeId, renderer: TraitRenderer) {
        renderers[traitShapeId] = renderer
    }

    /** Remove the renderer for [traitShapeId]. First-party renderers cannot be removed. */
    public fun remove(traitShapeId: ShapeId) {
        require(traitShapeId !in firstPartyIds) { "cannot remove first-party trait renderer for $traitShapeId" }
        renderers.remove(traitShapeId)
    }

    internal fun rendererFor(traitShapeId: ShapeId): TraitRenderer? = renderers[traitShapeId]

    /** The trait ids included in generated schemas (those with a registered renderer). */
    internal val includedTraitIds: Set<ShapeId> get() = renderers.keys

    public companion object {
        // valueless annotation traits: model trait id -> the runtime `object` symbol, rendered as `#T`
        private val annotationTraits: Map<String, Symbol> = mapOf(
            "smithy.api#required" to Schema.Traits.RequiredTrait,
            "smithy.api#sparse" to Schema.Traits.SparseTrait,
            "smithy.api#sensitive" to Schema.Traits.SensitiveTrait,
            "smithy.api#idempotencyToken" to Schema.Traits.IdempotencyTokenTrait,
            "smithy.api#streaming" to Schema.Traits.StreamingTrait,
            "smithy.api#requiresLength" to Schema.Traits.RequiresLengthTrait,
            "smithy.api#eventHeader" to Schema.Traits.EventHeaderTrait,
            "smithy.api#eventPayload" to Schema.Traits.EventPayloadTrait,
            "smithy.api#xmlAttribute" to Schema.Traits.XmlAttributeTrait,
            "smithy.api#xmlFlattened" to Schema.Traits.XmlFlattenedTrait,
            "smithy.api#httpLabel" to Schema.Traits.HttpLabelTrait,
            "smithy.api#httpPayload" to Schema.Traits.HttpPayloadTrait,
            "smithy.api#httpQueryParams" to Schema.Traits.HttpQueryParamsTrait,
            "smithy.api#httpResponseCode" to Schema.Traits.HttpResponseCodeTrait,
            "smithy.api#hostLabel" to Schema.Traits.HostLabelTrait,
        )

        /**
         * A [SchemaTraitExtension] pre-populated with first-party renderers, then customized by each of the
         * given [integrations] via [KotlinIntegration.customizeSchemaTraits].
         */
        public fun fromIntegrations(
            integrations: List<aws.smithy.kotlin.codegen.integration.KotlinIntegration>,
        ): SchemaTraitExtension = default().also { ext -> integrations.forEach { it.customizeSchemaTraits(ext) } }

        /** A [SchemaTraitExtension] pre-populated with all first-party serde-relevant trait renderers. */
        public fun default(): SchemaTraitExtension {
            val renderers = LinkedHashMap<ShapeId, TraitRenderer>()

            annotationTraits.forEach { (id, symbol) ->
                renderers[ShapeId.from(id)] = TraitRenderer { { writeInline("#T", symbol) } }
            }

            renderers[JsonNameTrait.ID] = stringTrait(Schema.Traits.JsonNameTrait) { (it as JsonNameTrait).value }
            renderers[XmlNameTrait.ID] = stringTrait(Schema.Traits.XmlNameTrait) { (it as XmlNameTrait).value }
            renderers[MediaTypeTrait.ID] = stringTrait(Schema.Traits.MediaTypeTrait) { (it as MediaTypeTrait).value }
            renderers[HttpHeaderTrait.ID] = stringTrait(Schema.Traits.HttpHeaderTrait) { (it as HttpHeaderTrait).value }
            renderers[HttpQueryTrait.ID] = stringTrait(Schema.Traits.HttpQueryTrait) { (it as HttpQueryTrait).value }
            renderers[HttpPrefixHeadersTrait.ID] =
                stringTrait(Schema.Traits.HttpPrefixHeadersTrait) { (it as HttpPrefixHeadersTrait).value }
            renderers[ContextParamTrait.ID] = stringTrait(Schema.Traits.ContextParamTrait) { (it as ContextParamTrait).name }

            renderers[XmlNamespaceTrait.ID] = TraitRenderer { t ->
                val ns = t as XmlNamespaceTrait
                val prefix = ns.prefix.orElse(null)
                val w: InlineKotlinWriter = {
                    if (prefix != null) {
                        writeInline("#T(#S, #S)", Schema.Traits.XmlNamespaceTrait, ns.uri, prefix)
                    } else {
                        writeInline("#T(#S)", Schema.Traits.XmlNamespaceTrait, ns.uri)
                    }
                }
                w
            }
            renderers[TimestampFormatTrait.ID] = TraitRenderer { t ->
                val enum = (t as TimestampFormatTrait).value.timestampFormatEnum
                val w: InlineKotlinWriter = {
                    writeInline("#T(#T.#L)", Schema.Traits.TimestampFormatTrait, Schema.Traits.TimestampFormat, enum)
                }
                w
            }
            renderers[AwsQueryErrorTrait.ID] = TraitRenderer { t ->
                val e = t as AwsQueryErrorTrait
                val w: InlineKotlinWriter = { writeInline("#T(#S, #L)", Schema.Traits.AwsQueryErrorTrait, e.code, e.httpResponseCode) }
                w
            }

            return SchemaTraitExtension(renderers, renderers.keys.toSet())
        }

        // helper for the common single-string-argument runtime trait constructors
        private fun stringTrait(symbol: Symbol, value: (Trait) -> String): TraitRenderer = TraitRenderer { t ->
            val v = value(t)
            val w: InlineKotlinWriter = { writeInline("#T(#S)", symbol, v) }
            w
        }
    }
}

private val String.timestampFormatEnum: String
    get() = when (this) {
        "epoch-seconds" -> "EPOCH_SECONDS"
        "date-time" -> "DATE_TIME"
        "http-date" -> "HTTP_DATE"
        else -> error("unknown timestampFormat '$this'")
    }
