/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

/**
 * `smithy.api#jsonName` — the wire name to use for a member under JSON protocols that honor it (restJson1).
 */
public class JsonNameTrait(public val value: String) : Trait {
    public companion object {
        public val ID: ShapeId = ShapeId.from("smithy.api#jsonName")
    }
    override val id: ShapeId get() = ID
    override fun toString(): String = "JsonName($value)"
}

/**
 * `smithy.api#xmlName` — the wire name to use for a member under XML protocols.
 */
public class XmlNameTrait(public val value: String) : Trait {
    public companion object {
        public val ID: ShapeId = ShapeId.from("smithy.api#xmlName")
    }
    override val id: ShapeId get() = ID
    override fun toString(): String = "XmlName($value)"
}

/**
 * The set of timestamp wire formats Smithy models can request via `smithy.api#timestampFormat`.
 */
public enum class TimestampFormat {
    EPOCH_SECONDS,
    DATE_TIME,
    HTTP_DATE,
}

/**
 * `smithy.api#timestampFormat` — overrides the protocol default timestamp format for a shape/member.
 */
public class TimestampFormatTrait(public val format: TimestampFormat) : Trait {
    public companion object {
        public val ID: ShapeId = ShapeId.from("smithy.api#timestampFormat")
    }
    override val id: ShapeId get() = ID
    override fun toString(): String = "TimestampFormat($format)"
}

/**
 * `smithy.api#required` — the member must be present. Modeled as a valueless annotation trait.
 */
public object RequiredTrait : Trait {
    public val ID: ShapeId = ShapeId.from("smithy.api#required")
    override val id: ShapeId get() = ID
    override fun toString(): String = "Required"
}

/**
 * `smithy.api#sparse` — a list/map may contain null values (and they must be preserved on the wire).
 */
public object SparseTrait : Trait {
    public val ID: ShapeId = ShapeId.from("smithy.api#sparse")
    override val id: ShapeId get() = ID
    override fun toString(): String = "Sparse"
}
