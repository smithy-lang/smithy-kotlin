/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schemas

import aws.smithy.kotlin.runtime.content.Document
import aws.smithy.kotlin.runtime.serde.ShapeId
import aws.smithy.kotlin.runtime.serde.ShapeType
import aws.smithy.kotlin.runtime.serde.Trait
import aws.smithy.kotlin.runtime.serde.codecs.Decoder
import aws.smithy.kotlin.runtime.serde.codecs.Encoder
import aws.smithy.kotlin.runtime.serde.codecs.decodeDocument
import aws.smithy.kotlin.runtime.serde.codecs.encodeDocument

public interface DocumentSchema : SerializableSchema<Document?>

private data class DocumentSchemaImpl(
    override val shapeId: ShapeId,
    override val traits: List<Trait>,
) : DocumentSchema {
    override val shapeType: ShapeType = ShapeType.DOCUMENT
    override fun deserialize(decoder: Decoder): Document? = decoder.decodeDocument()
    override fun serialize(encoder: Encoder, value: Document?) = encoder.encodeDocument(value)
}

public fun DocumentSchema(shapeId: ShapeId, traits: List<Trait>): DocumentSchema = DocumentSchemaImpl(shapeId, traits)
