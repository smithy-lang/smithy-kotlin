/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema.trait

import aws.smithy.kotlin.runtime.serde.schema.ShapeId
import aws.smithy.kotlin.runtime.serde.schema.shapeId

// ── Serialization & protocol traits ─────────────────────────────────────────────────────────────

/** `smithy.api#jsonName` — the wire name for a member under JSON protocols that honor it (restJson1). */
public class JsonNameTrait(public val value: String) : Trait {
    public companion object {
        public val ID: ShapeId = shapeId("smithy.api#jsonName")
    }
    override val id: ShapeId get() = ID
    override fun toString(): String = "JsonName($value)"
}

/** `smithy.api#xmlName` — the wire name for a member under XML protocols. */
public class XmlNameTrait(public val value: String) : Trait {
    public companion object {
        public val ID: ShapeId = shapeId("smithy.api#xmlName")
    }
    override val id: ShapeId get() = ID
    override fun toString(): String = "XmlName($value)"
}

/** `smithy.api#xmlAttribute` — serialize the member as an XML attribute rather than a nested element. */
public object XmlAttributeTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#xmlAttribute")
    override val id: ShapeId get() = ID
    override fun toString(): String = "XmlAttribute"
}

/** `smithy.api#xmlFlattened` — serialize a list/map without a wrapping element. */
public object XmlFlattenedTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#xmlFlattened")
    override val id: ShapeId get() = ID
    override fun toString(): String = "XmlFlattened"
}

/** `smithy.api#xmlNamespace` — the XML namespace ([uri], optional [prefix]) applied to a shape's element. */
public class XmlNamespaceTrait(public val uri: String, public val prefix: String? = null) : Trait {
    public companion object {
        public val ID: ShapeId = shapeId("smithy.api#xmlNamespace")
    }
    override val id: ShapeId get() = ID
    override fun toString(): String = "XmlNamespace($uri${prefix?.let { ", $it" } ?: ""})"
}

/** `smithy.api#mediaType` — the media type of a string/blob payload. */
public class MediaTypeTrait(public val value: String) : Trait {
    public companion object {
        public val ID: ShapeId = shapeId("smithy.api#mediaType")
    }
    override val id: ShapeId get() = ID
    override fun toString(): String = "MediaType($value)"
}

/** The set of timestamp wire formats requestable via `smithy.api#timestampFormat`. */
public enum class TimestampFormat {
    EPOCH_SECONDS,
    DATE_TIME,
    HTTP_DATE,
}

/** `smithy.api#timestampFormat` — overrides the protocol default timestamp format for a shape/member. */
public class TimestampFormatTrait(public val format: TimestampFormat) : Trait {
    public companion object {
        public val ID: ShapeId = shapeId("smithy.api#timestampFormat")
    }
    override val id: ShapeId get() = ID
    override fun toString(): String = "TimestampFormat($format)"
}

/** `aws.protocols#awsQueryError` — the error [code] and HTTP [httpResponseCode] for an awsQuery error shape. */
public class AwsQueryErrorTrait(public val code: String, public val httpResponseCode: Int) : Trait {
    public companion object {
        public val ID: ShapeId = shapeId("aws.protocols#awsQueryError")
    }
    override val id: ShapeId get() = ID
    override fun toString(): String = "AwsQueryError($code, $httpResponseCode)"
}

// ── Type-refinement traits ──────────────────────────────────────────────────────────────────────

/** `smithy.api#required` — the member must be present (valueless annotation trait). */
public object RequiredTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#required")
    override val id: ShapeId get() = ID
    override fun toString(): String = "Required"
}

/** `smithy.api#sparse` — a list/map may contain null values, preserved on the wire. */
public object SparseTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#sparse")
    override val id: ShapeId get() = ID
    override fun toString(): String = "Sparse"
}

// ── Documentation traits ────────────────────────────────────────────────────────────────────────

/** `smithy.api#sensitive` — the value should be redacted from logs and other output. */
public object SensitiveTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#sensitive")
    override val id: ShapeId get() = ID
    override fun toString(): String = "Sensitive"
}

// ── Behavior traits ─────────────────────────────────────────────────────────────────────────────

/** `smithy.api#idempotencyToken` — a token member auto-populated when not set by the caller. */
public object IdempotencyTokenTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#idempotencyToken")
    override val id: ShapeId get() = ID
    override fun toString(): String = "IdempotencyToken"
}

// ── Streaming traits ────────────────────────────────────────────────────────────────────────────

/** `smithy.api#streaming` — the blob/union is a data stream. */
public object StreamingTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#streaming")
    override val id: ShapeId get() = ID
    override fun toString(): String = "Streaming"
}

/** `smithy.api#requiresLength` — a streaming blob whose length must be known before transmission. */
public object RequiresLengthTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#requiresLength")
    override val id: ShapeId get() = ID
    override fun toString(): String = "RequiresLength"
}

/** `smithy.api#eventHeader` — serialize the member as an event-stream message header. */
public object EventHeaderTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#eventHeader")
    override val id: ShapeId get() = ID
    override fun toString(): String = "EventHeader"
}

/** `smithy.api#eventPayload` — serialize the member as the event-stream message payload. */
public object EventPayloadTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#eventPayload")
    override val id: ShapeId get() = ID
    override fun toString(): String = "EventPayload"
}

// ── HTTP binding traits ─────────────────────────────────────────────────────────────────────────

/** `smithy.api#httpHeader` — bind the member to the HTTP header [name]. */
public class HttpHeaderTrait(public val name: String) : Trait {
    public companion object {
        public val ID: ShapeId = shapeId("smithy.api#httpHeader")
    }
    override val id: ShapeId get() = ID
    override fun toString(): String = "HttpHeader($name)"
}

/** `smithy.api#httpLabel` — bind the member to a URI path label (valueless annotation trait). */
public object HttpLabelTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#httpLabel")
    override val id: ShapeId get() = ID
    override fun toString(): String = "HttpLabel"
}

/** `smithy.api#httpPayload` — bind the member as the entire HTTP payload (valueless annotation trait). */
public object HttpPayloadTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#httpPayload")
    override val id: ShapeId get() = ID
    override fun toString(): String = "HttpPayload"
}

/** `smithy.api#httpPrefixHeaders` — bind a map member to HTTP headers sharing the given [prefix]. */
public class HttpPrefixHeadersTrait(public val prefix: String) : Trait {
    public companion object {
        public val ID: ShapeId = shapeId("smithy.api#httpPrefixHeaders")
    }
    override val id: ShapeId get() = ID
    override fun toString(): String = "HttpPrefixHeaders($prefix)"
}

/** `smithy.api#httpQuery` — bind the member to the query-string parameter [name]. */
public class HttpQueryTrait(public val name: String) : Trait {
    public companion object {
        public val ID: ShapeId = shapeId("smithy.api#httpQuery")
    }
    override val id: ShapeId get() = ID
    override fun toString(): String = "HttpQuery($name)"
}

/** `smithy.api#httpQueryParams` — bind a map member to the query string (valueless annotation trait). */
public object HttpQueryParamsTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#httpQueryParams")
    override val id: ShapeId get() = ID
    override fun toString(): String = "HttpQueryParams"
}

/** `smithy.api#httpResponseCode` — bind the member to the HTTP response status code (valueless). */
public object HttpResponseCodeTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#httpResponseCode")
    override val id: ShapeId get() = ID
    override fun toString(): String = "HttpResponseCode"
}

// ── Endpoint traits ─────────────────────────────────────────────────────────────────────────────

/** `smithy.api#hostLabel` — bind the member to a label in the operation's `endpoint` host prefix. */
public object HostLabelTrait : Trait {
    public val ID: ShapeId = shapeId("smithy.api#hostLabel")
    override val id: ShapeId get() = ID
    override fun toString(): String = "HostLabel"
}

// ── Rules-engine traits ─────────────────────────────────────────────────────────────────────────

/** `smithy.rules#contextParam` — binds an input member to the endpoint-rules context parameter [name]. */
public class ContextParamTrait(public val name: String) : Trait {
    public companion object {
        public val ID: ShapeId = shapeId("smithy.rules#contextParam")
    }
    override val id: ShapeId get() = ID
    override fun toString(): String = "ContextParam($name)"
}
