/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde

@JvmInline
public value class ShapeId(public val id: String)

public interface Shape {
    public val shapeId: ShapeId
    public val traits: List<Trait>
}

public enum class ShapeType {
    BIG_DECIMAL,
    BIG_INTEGER,
    BLOB,
    BOOLEAN,
    BYTE,
    DOCUMENT,
    DOUBLE,
    ENUM,
    FLOAT,
    INTEGER,
    INTEGER_ENUM,
    LIST,
    LONG,
    MAP,
    MEMBER,
    OPERATION,
    SERVICE,
    SET,
    SHORT,
    STRING,
    TIMESTAMP,
    STRUCTURE,
    UNION,
}

public sealed interface Schema : Shape {
    public val shapeType: ShapeType
}

public interface ScalarSchema : Schema

private data class ScalarSchemaImpl(
    override val shapeId: ShapeId,
    override val shapeType: ShapeType,
    override val traits: List<Trait>,
) : ScalarSchema

public fun ScalarSchema(
    shapeId: ShapeId,
    shapeType: ShapeType,
    traits: List<Trait>,
): ScalarSchema = ScalarSchemaImpl(shapeId, shapeType, traits)

public interface MemberSchema : Schema {
    public val target: Schema
}

private data class MemberSchemaImpl(
    override val shapeId: ShapeId,
    override val shapeType: ShapeType,
    override val target: Schema,
    override val traits: List<Trait>,
) : MemberSchema

public fun MemberSchema(
    shapeId: ShapeId,
    shapeType: ShapeType,
    target: Schema,
    traits: List<Trait>,
): MemberSchema = MemberSchemaImpl(shapeId, shapeType, target, traits)

public fun MemberSchema(
    shapeId: ShapeId,
    shapeType: ShapeType,
    target: Lazy<Schema>,
    traits: List<Trait>,
): MemberSchema = object : MemberSchema {
    override val shapeId = shapeId
    override val shapeType = shapeType
    override val target by lazy { target.value }
    override val traits = traits
}

public interface StructureSchema : Schema {
    public val members: List<Schema>
}

private data class StructureSchemaImpl(
    override val shapeId: ShapeId,
    override val shapeType: ShapeType,
    override val members: List<Schema>,
    override val traits: List<Trait>,
) : StructureSchema

public fun StructureSchema(
    shapeId: ShapeId,
    shapeType: ShapeType,
    members: List<Schema>,
    traits: List<Trait>,
): StructureSchema = StructureSchemaImpl(shapeId, shapeType, members, traits)

public interface ListSchema : Schema {
    public val member: Schema
}

private data class ListSchemaImpl(
    override val shapeId: ShapeId,
    override val shapeType: ShapeType,
    override val member: Schema,
    override val traits: List<Trait>,
) : ListSchema

public fun ListSchema(
    shapeId: ShapeId,
    shapeType: ShapeType,
    member: Schema,
    traits: List<Trait>,
): ListSchema = ListSchemaImpl(shapeId, shapeType, member, traits)

public interface MapSchema : Schema {
    public val key: Schema
    public val value: Schema
}

private data class MapSchemaImpl(
    override val shapeId: ShapeId,
    override val shapeType: ShapeType,
    override val key: Schema,
    override val value: Schema,
    override val traits: List<Trait>,
) : MapSchema

public fun MapSchema(
    shapeId: ShapeId,
    shapeType: ShapeType,
    key: Schema,
    value: Schema,
    traits: List<Trait>,
): MapSchema = MapSchemaImpl(shapeId, shapeType, key, value, traits)

public interface OperationSchema : Schema {
    public val input: StructureSchema
    public val output: StructureSchema
}

private data class OperationSchemaImpl(
    override val shapeId: ShapeId,
    override val shapeType: ShapeType,
    override val input: StructureSchema,
    override val output: StructureSchema,
    override val traits: List<Trait>,
) : OperationSchema

public fun OperationSchema(
    shapeId: ShapeId,
    shapeType: ShapeType,
    input: StructureSchema,
    output: StructureSchema,
    traits: List<Trait>,
): OperationSchema = OperationSchemaImpl(shapeId, shapeType, input, output, traits)

public interface ServiceSchema : Schema {
    public val operations: List<OperationSchema>
    public val errors: List<StructureSchema>
}

private data class ServiceSchemaImpl(
    override val shapeId: ShapeId,
    override val shapeType: ShapeType,
    override val operations: List<OperationSchema>,
    override val errors: List<StructureSchema>,
    override val traits: List<Trait>,
) : ServiceSchema

public fun ServiceSchema(
    shapeId: ShapeId,
    shapeType: ShapeType,
    operations: List<OperationSchema>,
    errors: List<StructureSchema>,
    traits: List<Trait>,
): ServiceSchema = ServiceSchemaImpl(shapeId, shapeType, operations, errors, traits)
