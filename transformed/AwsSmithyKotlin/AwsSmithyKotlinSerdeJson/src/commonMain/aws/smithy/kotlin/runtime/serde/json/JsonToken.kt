/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.InternalApi

/**
 * Raw tokens produced when reading a JSON document as a stream
 */
@InternalApi
public sealed class JsonToken {
    /**
     * The opening of a JSON array '['
     */
    @InternalApi
    public object BeginArray : JsonToken()

    /**
     * The closing of a JSON array ']'
     */
    @InternalApi
    public object EndArray : JsonToken()

    /**
     * The opening of a JSON object '{'
     */
    @InternalApi
    public object BeginObject : JsonToken()

    /**
     * The closing of a JSON object '}'
     */
    @InternalApi
    public object EndObject : JsonToken()

    /**
     * A JSON property name
     */
    @InternalApi
    public data class Name(val value: kotlin.String) : JsonToken()

    /**
     * A JSON string
     */
    @InternalApi
    public data class String(val value: kotlin.String) : JsonToken()

    /**
     * A JSON number (note the raw string value of the number is returned, you are responsible for converting
     * to a concrete [Number] type)
     */
    @InternalApi
    public data class Number(val value: kotlin.String) : JsonToken()

    /**
     * A JSON boolean
     */
    @InternalApi
    public data class Bool(val value: Boolean) : JsonToken()

    /**
     * A JSON 'null'
     */
    @InternalApi
    public object Null : JsonToken()

    /**
     * The end of the JSON stream to signal that the JSON-encoded value has no more
     * tokens
     */
    @InternalApi
    public object EndDocument : JsonToken()

    override fun toString(): kotlin.String = when (this) {
        BeginArray -> "BeginArray"
        EndArray -> "EndArray"
        BeginObject -> "BeginObject"
        EndObject -> "EndObject"
        is Name -> "Name(${this.value})"
        is String -> "String(${this.value})"
        is Number -> "Number(${this.value})"
        is Bool -> "Bool(${this.value})"
        Null -> "Null"
        EndDocument -> "EndDocument"
    }
}
