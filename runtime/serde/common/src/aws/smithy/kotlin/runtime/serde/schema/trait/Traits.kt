/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema.trait

import aws.smithy.kotlin.runtime.serde.schema.ShapeId
import aws.smithy.kotlin.runtime.serde.schema.shapeId
import aws.smithy.kotlin.runtime.time.TimestampFormat

/** The wire name for a member under JSON protocols. */
public class JsonNameTrait(public val value: String) : Trait {
    public companion object {
        public val ID: ShapeId = shapeId("smithy.api#jsonName")
    }
    override val id: ShapeId get() = ID
    override fun toString(): String = "JsonName($value)"
}

/** The wire name for a member under XML protocols. */
public class XmlNameTrait(public val value: String) : Trait {
    public companion object {
        public val ID: ShapeId = shapeId("smithy.api#xmlName")
    }
    override val id: ShapeId get() = ID
    override fun toString(): String = "XmlName($value)"
}

/** Serialize the member as an XML attribute rather than a nested element. */
public object XmlAttributeTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#xmlAttribute")
    override val id: ShapeId get() = ID
    override fun toString(): String = "XmlAttribute"
}

/** Serialize a list/map without a wrapping element. */
public object XmlFlattenedTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#xmlFlattened")
    override val id: ShapeId get() = ID
    override fun toString(): String = "XmlFlattened"
}

/** The XML namespace ([uri], optional [prefix]) applied to a shape's element. */
public class XmlNamespaceTrait(public val uri: String, public val prefix: String? = null) : Trait {
    public companion object {
        public val ID: ShapeId = shapeId("smithy.api#xmlNamespace")
    }
    override val id: ShapeId get() = ID
    override fun toString(): String = "XmlNamespace($uri${prefix?.let { ", $it" } ?: ""})"
}

/** The media type of a string/blob payload. */
public class MediaTypeTrait(public val value: String) : Trait {
    public companion object {
        public val ID: ShapeId = shapeId("smithy.api#mediaType")
    }
    override val id: ShapeId get() = ID
    override fun toString(): String = "MediaType($value)"
}

/** Overrides the protocol default timestamp format for a shape/member. */
public class TimestampFormatTrait(public val format: TimestampFormat) : Trait {
    public companion object {
        public val ID: ShapeId = shapeId("smithy.api#timestampFormat")
    }
    override val id: ShapeId get() = ID
    override fun toString(): String = "TimestampFormat($format)"
}

/** The error [code] and HTTP [httpResponseCode] for an awsQuery error shape. */
public class AwsQueryErrorTrait(public val code: String, public val httpResponseCode: Int) : Trait {
    public companion object {
        public val ID: ShapeId = shapeId("aws.protocols#awsQueryError")
    }
    override val id: ShapeId get() = ID
    override fun toString(): String = "AwsQueryError($code, $httpResponseCode)"
}

/** The member must be present (valueless annotation trait). */
public object RequiredTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#required")
    override val id: ShapeId get() = ID
    override fun toString(): String = "Required"
}

/** A list/map may contain null values, preserved on the wire. */
public object SparseTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#sparse")
    override val id: ShapeId get() = ID
    override fun toString(): String = "Sparse"
}

/** The value should be redacted from logs and other output. */
public object SensitiveTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#sensitive")
    override val id: ShapeId get() = ID
    override fun toString(): String = "Sensitive"
}

/** A token member auto-populated when not set by the caller. */
public object IdempotencyTokenTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#idempotencyToken")
    override val id: ShapeId get() = ID
    override fun toString(): String = "IdempotencyToken"
}

/** The blob/union is a data stream. */
public object StreamingTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#streaming")
    override val id: ShapeId get() = ID
    override fun toString(): String = "Streaming"
}

/** A streaming blob whose length must be known before transmission. */
public object RequiresLengthTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#requiresLength")
    override val id: ShapeId get() = ID
    override fun toString(): String = "RequiresLength"
}

/** Serialize the member as an event-stream message header. */
public object EventHeaderTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#eventHeader")
    override val id: ShapeId get() = ID
    override fun toString(): String = "EventHeader"
}

/** Serialize the member as the event-stream message payload. */
public object EventPayloadTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#eventPayload")
    override val id: ShapeId get() = ID
    override fun toString(): String = "EventPayload"
}

/** Bind the member to the HTTP header [name]. */
public class HttpHeaderTrait(public val name: String) : Trait {
    public companion object {
        public val ID: ShapeId = shapeId("smithy.api#httpHeader")
    }
    override val id: ShapeId get() = ID
    override fun toString(): String = "HttpHeader($name)"
}

/** Bind the member to a URI path label (valueless annotation trait). */
public object HttpLabelTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#httpLabel")
    override val id: ShapeId get() = ID
    override fun toString(): String = "HttpLabel"
}

/** Bind the member as the entire HTTP payload (valueless annotation trait). */
public object HttpPayloadTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#httpPayload")
    override val id: ShapeId get() = ID
    override fun toString(): String = "HttpPayload"
}

/** Bind a map member to HTTP headers sharing the given [prefix]. */
public class HttpPrefixHeadersTrait(public val prefix: String) : Trait {
    public companion object {
        public val ID: ShapeId = shapeId("smithy.api#httpPrefixHeaders")
    }
    override val id: ShapeId get() = ID
    override fun toString(): String = "HttpPrefixHeaders($prefix)"
}

/** Bind the member to the query-string parameter [name]. */
public class HttpQueryTrait(public val name: String) : Trait {
    public companion object {
        public val ID: ShapeId = shapeId("smithy.api#httpQuery")
    }
    override val id: ShapeId get() = ID
    override fun toString(): String = "HttpQuery($name)"
}

/** Bind a map member to the query string (valueless annotation trait). */
public object HttpQueryParamsTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#httpQueryParams")
    override val id: ShapeId get() = ID
    override fun toString(): String = "HttpQueryParams"
}

/** Bind the member to the HTTP response status code (valueless). */
public object HttpResponseCodeTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#httpResponseCode")
    override val id: ShapeId get() = ID
    override fun toString(): String = "HttpResponseCode"
}

/** Bind the member to a label in the operation's `endpoint` host prefix. */
public object HostLabelTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#hostLabel")
    override val id: ShapeId get() = ID
    override fun toString(): String = "HostLabel"
}

/** Binds an input member to the endpoint-rules context parameter [name]. */
public class ContextParamTrait(public val name: String) : Trait {
    public companion object {
        public val ID: ShapeId = shapeId("smithy.rules#contextParam")
    }
    override val id: ShapeId get() = ID
    override fun toString(): String = "ContextParam($name)"
}
