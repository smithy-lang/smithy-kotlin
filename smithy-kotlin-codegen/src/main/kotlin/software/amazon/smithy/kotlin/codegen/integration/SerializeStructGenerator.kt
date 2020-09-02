/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.amazon.smithy.kotlin.codegen.integration

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.kotlin.codegen.KotlinWriter
import software.amazon.smithy.kotlin.codegen.defaultName
import software.amazon.smithy.kotlin.codegen.withBlock
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait

private enum class SerializeLocation(val serializerFn: String) {
    // class/struct field
    Field("field"),

    // Map value
    Map("entry")
}

/**
 * Container for serialization information for a particular shape being serialized to
 * a specific [SerializeLocation]
 *
 * @property fn The name of the serialization function to use
 * @property encodedValue The value to pass to the serialization function
 */
private data class SerializeInfo(val fn: String, val encodedValue: String)

/**
 * Generate serialization for members bound to the payload.
 *
 * e.g.
 * ```
 * serializer.serializeStruct(null) {
 *     input.field1?.let { field(FIELD1_DESCRIPTOR, it) }
 *     input.field2?.let { field(FIELD2_DESCRIPTOR, it) }
 * }
 * ```
 */
class SerializeStructGenerator(
    private val ctx: ProtocolGenerator.GenerationContext,
    private val members: List<MemberShape>,
    private val writer: KotlinWriter,
    private val defaultTimestampFormat: TimestampFormatTrait.Format
) {

    fun render() {
        writer.withBlock("serializer.serializeStruct {", "}") {
            members.sortedBy { it.memberName }.forEach { member ->
                val target = ctx.model.expectShape(member.target)
                when (target.type) {
                    ShapeType.LIST, ShapeType.SET -> renderListMemberSerializer(member)
                    ShapeType.MAP -> renderMapMemberSerializer(member)
                    ShapeType.DOCUMENT -> {
                        // TODO - implement document type support
                    }
                    else -> {
                        val (serializeFn, encoded) = serializationForShape(member)
                        writer.write("input.\$L?.let { $serializeFn(\$L, $encoded) }", member.defaultName(), member.descriptorName())
                    }
                }
            }
        }
    }

    /**
     * get the serialization functiona and encoded value for the given [Shape], this only handles "primitive" types,
     * collections should be handled separately
     * @param shape The shape to be serialized
     * @param identifier The name of the identifier to be passed to the serialization function
     * @param serializeLocation The location being serialized to
     */
    private fun serializationForShape(
        shape: Shape,
        identifier: String = "it",
        serializeLocation: SerializeLocation = SerializeLocation.Field
    ): SerializeInfo {
        // target shape type to deserialize is either the shape itself or member.target
        val target = when (shape) {
            is MemberShape -> ctx.model.expectShape(shape.target)
            else -> shape
        }

        // our current generation of serializer interfaces unfortunately uses different names depending on whether
        // you are serializing struct fields, list elements, map entries, etc.
        var serializerFn = serializeLocation.serializerFn

        val encoded = when (target.type) {
            ShapeType.BOOLEAN, ShapeType.BYTE,
            ShapeType.SHORT, ShapeType.INTEGER, ShapeType.LONG,
            ShapeType.FLOAT, ShapeType.DOUBLE -> identifier
            ShapeType.BLOB -> {
                importBase64Utils(writer)
                "$identifier.encodeBase64String()"
            }
            ShapeType.TIMESTAMP -> {
                importTimestampFormat(writer)
                val tsFormat = shape
                    .getTrait(TimestampFormatTrait::class.java)
                    .map { it.format }
                    .orElse(defaultTimestampFormat)

                if (tsFormat == TimestampFormatTrait.Format.EPOCH_SECONDS) {
                    serializerFn = "raw${serializerFn.capitalize()}"
                }

                formatInstant(identifier, tsFormat, forceString = true)
            }
            ShapeType.STRING -> when {
                target.hasTrait(EnumTrait::class.java) -> {
                    "$identifier.value"
                }
                else -> identifier
            }
            ShapeType.STRUCTURE, ShapeType.UNION -> {
                // nested structures and unions can be serialized directly through their Serializer (via SdkSerializable)
                val symbol = ctx.symbolProvider.toSymbol(target)
                val memberSerializerName = "${symbol.name}Serializer"
                // invoke the ctor of the serializer to delegate to and pass the value
                "$memberSerializerName($identifier)"
            }
            else -> throw CodegenException("unknown deserializer for member: $shape; target: $target")
        }

        return SerializeInfo(serializerFn, encoded)
    }

    /**
     * render serialization for a struct member of type "list"
     */
    private fun renderListMemberSerializer(member: MemberShape) {
        val memberName = member.defaultName()
        val listTarget = ctx.model.expectShape(member.target) as CollectionShape
        val target = ctx.model.expectShape(listTarget.member.target)
        writer.withBlock("if (input.$memberName != null) {", "}") {
            writer.withBlock("listField(${member.descriptorName()}) {", "}") {
                renderListSerializer(ctx, "input.$memberName", target, writer)
            }
        }
    }

    // internal details of rendering a list type
    private fun renderListSerializer(
        ctx: ProtocolGenerator.GenerationContext,
        collectionName: String,
        targetShape: Shape,
        writer: KotlinWriter,
        level: Int = 0
    ) {
        val iteratorName = "m$level"
        writer.openBlock("for(\$L in \$L) {", iteratorName, collectionName)
            .call {
                when (targetShape) {
                    is CollectionShape -> {
                        // nested list
                        val nestedTarget = ctx.model.expectShape(targetShape.member.target)
                        writer.withBlock("serializer.serializeList {", "}") {
                            renderListSerializer(ctx, iteratorName, nestedTarget, writer, level + 1)
                        }
                    }
                    is TimestampShape -> {
                        val bindingIndex = ctx.model.getKnowledge(HttpBindingIndex::class.java)
                        val tsFormat = bindingIndex.determineTimestampFormat(
                            targetShape,
                            HttpBinding.Location.DOCUMENT,
                            defaultTimestampFormat
                        )
                        val formatted = formatInstant(iteratorName, tsFormat, forceString = true)
                        val serializeMethod = when (tsFormat) {
                            TimestampFormatTrait.Format.EPOCH_SECONDS -> "serializeRaw"
                            else -> "serialize"
                        }
                        writer.write("$serializeMethod(\$L)", formatted)
                        importTimestampFormat(writer)
                    }
                    is StructureShape, is UnionShape -> {
                        val targetSymbol = ctx.symbolProvider.toSymbol(targetShape)
                        val wrappedIterator = "${targetSymbol.name}Serializer($iteratorName)"
                        writer.write("serializeSdkSerializable(\$L)", wrappedIterator)
                    }
                    is BlobShape -> {
                        importBase64Utils(writer)
                        writer.write("serializeString($iteratorName.encodeBase64String())")
                    }
                    else -> {
                        // primitive we can serialize
                        val iter = if (targetShape.isStringShape && targetShape.hasTrait(EnumTrait::class.java)) {
                            "$iteratorName.value"
                        } else {
                            iteratorName
                        }
                        writer.write("\$L(\$L)", targetShape.type.primitiveSerializerFunctionName(), iter)
                    }
                }
            }
            .closeBlock("}")
    }

    /**
     * Render serialization for a struct member of type "map"
     */
    private fun renderMapMemberSerializer(member: MemberShape) {
        val memberName = member.defaultName()
        val mapShape = ctx.model.expectShape(member.target).asMapShape().get()
        val valueTargetShape = ctx.model.expectShape(mapShape.value.target)

        writer.withBlock("if (input.$memberName != null) {", "}") {
            writer.withBlock("mapField(${member.descriptorName()}) {", "}") {
                val (serializeFn, encoded) = serializationForShape(valueTargetShape, "value", SerializeLocation.Map)
                write("input.$memberName.forEach { (key, value) -> $serializeFn(key, $encoded) }")
            }
        }
    }
}

/**
 * Get the name of the `PrimitiveSerializer` function name for the corresponding shape type
 * @throws CodegenException when no known function name for the given type is known to exist
 */
private fun ShapeType.primitiveSerializerFunctionName(): String {
    val suffix = when (this) {
        ShapeType.BOOLEAN -> "Boolean"
        ShapeType.STRING -> "String"
        ShapeType.BYTE -> "Byte"
        ShapeType.SHORT -> "Short"
        ShapeType.INTEGER -> "Int"
        ShapeType.LONG -> "Long"
        ShapeType.FLOAT -> "Float"
        ShapeType.DOUBLE -> "Double"
        ShapeType.STRUCTURE, ShapeType.UNION -> "SdkSerializable"
        else -> throw CodegenException("$this has no primitive serialize function on the Serializer interface")
    }
    return "serialize$suffix"
}
