/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.core

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.model.namespace

/**
 * Commonly used runtime types. Provides a single definition of a runtime symbol such that codegen isn't littered
 * with inline symbol creation which makes refactoring of the runtime more difficult and error prone.
 *
 * NOTE: Not all symbols need be added here but it doesn't hurt to define runtime symbols once.
 */
object RuntimeTypes {
    object Http {
        val HttpBody = runtimeSymbol("HttpBody", KotlinDependency.CLIENT_RT_HTTP)
        val HttpMethod = runtimeSymbol("HttpMethod", KotlinDependency.CLIENT_RT_HTTP)
        val SdkHttpClient = runtimeSymbol("SdkHttpClient", KotlinDependency.CLIENT_RT_HTTP)
        val SdkHttpClientFn = runtimeSymbol("sdkHttpClient", KotlinDependency.CLIENT_RT_HTTP)
        val ByteArrayContent = runtimeSymbol("ByteArrayContent", KotlinDependency.CLIENT_RT_HTTP, "content")
        val MutateHeadersMiddleware = runtimeSymbol("MutateHeaders", KotlinDependency.CLIENT_RT_HTTP, "middleware")
        val encodeLabel = runtimeSymbol("encodeLabel", KotlinDependency.CLIENT_RT_HTTP, "util")
        val readAll = runtimeSymbol("readAll", KotlinDependency.CLIENT_RT_HTTP)
        val parameters = runtimeSymbol("parameters", KotlinDependency.CLIENT_RT_HTTP)
        val toByteStream = runtimeSymbol("toByteStream", KotlinDependency.CLIENT_RT_HTTP)
        val toHttpBody = runtimeSymbol("toHttpBody", KotlinDependency.CLIENT_RT_HTTP)
        // FIXME ~ remove and references to allSymbols should be replaced w/ a dependency set specific to the codegen target.
        val allSymbols = setOf(HttpBody, HttpMethod, readAll, parameters, toByteStream, toHttpBody, ByteArrayContent, MutateHeadersMiddleware, encodeLabel, SdkHttpClient, SdkHttpClientFn)

        object Request {
            val HttpRequest = runtimeSymbol("HttpRequest", KotlinDependency.CLIENT_RT_HTTP, "request")
            val HttpRequestBuilder = runtimeSymbol("HttpRequestBuilder", KotlinDependency.CLIENT_RT_HTTP, "request")
            val url = runtimeSymbol("url", KotlinDependency.CLIENT_RT_HTTP, "request")
            val headers = runtimeSymbol("headers", KotlinDependency.CLIENT_RT_HTTP, "request")
            // FIXME ~ remove and references to allSymbols should be replaced w/ a dependency set specific to the codegen target.
            val allSymbols = setOf(HttpRequest, HttpRequestBuilder, url, headers)
        }

        object Response {
            val HttpCall = runtimeSymbol("HttpCall", KotlinDependency.CLIENT_RT_HTTP, "response")
            val HttpResponse = runtimeSymbol("HttpResponse", KotlinDependency.CLIENT_RT_HTTP, "response")
            // FIXME ~ remove and references to allSymbols should be replaced w/ a dependency set specific to the codegen target.
            val allSymbols = setOf(HttpCall, HttpResponse)
        }

        object Operation {
            val HttpDeserialize = runtimeSymbol("HttpDeserialize", KotlinDependency.CLIENT_RT_HTTP, "operation")
            val HttpSerialize = runtimeSymbol("HttpSerialize", KotlinDependency.CLIENT_RT_HTTP, "operation")
            val SdkHttpOperation = runtimeSymbol("SdkHttpOperation", KotlinDependency.CLIENT_RT_HTTP, "operation")
            val context = runtimeSymbol("context", KotlinDependency.CLIENT_RT_HTTP, "operation")
            val roundTrip = runtimeSymbol("roundTrip", KotlinDependency.CLIENT_RT_HTTP, "operation")
            val execute = runtimeSymbol("execute", KotlinDependency.CLIENT_RT_HTTP, "operation")
            // FIXME ~ remove and references to allSymbols should be replaced w/ a dependency set specific to the codegen target.
            val allSymbols = setOf(HttpSerialize, HttpDeserialize, SdkHttpOperation, context, roundTrip, execute)
        }

        object Engine {
            val HttpClientEngineConfig = runtimeSymbol("HttpClientEngineConfig", KotlinDependency.CLIENT_RT_HTTP, "engine")
        }
    }

    object Core {
        val IdempotencyTokenProviderExt = runtimeSymbol("idempotencyTokenProvider", KotlinDependency.CLIENT_RT_CORE, "client")
        val ExecutionContext = runtimeSymbol("ExecutionContext", KotlinDependency.CLIENT_RT_CORE, "client")
        val ErrorMetadata = runtimeSymbol("ErrorMetadata", KotlinDependency.CLIENT_RT_CORE)
        val ServiceErrorMetadata = runtimeSymbol("ServiceErrorMetadata", KotlinDependency.CLIENT_RT_CORE)
        val Instant = runtimeSymbol("Instant", KotlinDependency.CLIENT_RT_CORE, "time")
        val TimestampFormat = runtimeSymbol("TimestampFormat", KotlinDependency.CLIENT_RT_CORE, "time")

        object Content {
            val ByteArrayContent = runtimeSymbol("ByteArrayContent", KotlinDependency.CLIENT_RT_CORE, "content")
            val ByteStream = runtimeSymbol("ByteStream", KotlinDependency.CLIENT_RT_CORE, "content")
            val StringContent = runtimeSymbol("StringContent", KotlinDependency.CLIENT_RT_CORE, "content")
            val toByteArray = runtimeSymbol("toByteArray", KotlinDependency.CLIENT_RT_CORE, "content")
            // FIXME ~ remove and references to allSymbols should be replaced w/ a dependency set specific to the codegen target.
            val allSymbols = listOf(ByteArrayContent, ByteStream, StringContent, toByteArray)
        }
    }

    object Utils {
        val AttributeKey = runtimeSymbol("AttributeKey", KotlinDependency.CLIENT_RT_UTILS)
    }

    object Serde {
        val Serializer = runtimeSymbol("Serializer", KotlinDependency.CLIENT_RT_SERDE)
        val Deserializer = runtimeSymbol("Deserializer", KotlinDependency.CLIENT_RT_SERDE)
        val SdkFieldDescriptor = runtimeSymbol("SdkFieldDescriptor", KotlinDependency.CLIENT_RT_SERDE)
        val SdkObjectDescriptor = runtimeSymbol("SdkObjectDescriptor", KotlinDependency.CLIENT_RT_SERDE)
        val SerialKind = runtimeSymbol("SerialKind", KotlinDependency.CLIENT_RT_SERDE)
        val SerializationException = runtimeSymbol("SerializationException", KotlinDependency.CLIENT_RT_SERDE)
        val DeserializationException = runtimeSymbol("DeserializationException", KotlinDependency.CLIENT_RT_SERDE)

        val serializeStruct = runtimeSymbol("serializeStruct", KotlinDependency.CLIENT_RT_SERDE)
        val serializeList = runtimeSymbol("serializeList", KotlinDependency.CLIENT_RT_SERDE)
        val serializeMap = runtimeSymbol("serializeMap", KotlinDependency.CLIENT_RT_SERDE)

        val deserializeStruct = runtimeSymbol("deserializeStruct", KotlinDependency.CLIENT_RT_SERDE)
        val deserializeList = runtimeSymbol("deserializeList", KotlinDependency.CLIENT_RT_SERDE)
        val deserializeMap = runtimeSymbol("deserializeMap", KotlinDependency.CLIENT_RT_SERDE)
        val asSdkSerializable = runtimeSymbol("asSdkSerializable", KotlinDependency.CLIENT_RT_SERDE)
        val field = runtimeSymbol("field", KotlinDependency.CLIENT_RT_SERDE)

        // FIXME ~ remove and references to allSymbols should be replaced w/ a dependency set specific to the codegen target.
        val allSymbols = setOf(
            Serializer, Deserializer, SdkFieldDescriptor, SdkObjectDescriptor,
            SerialKind, deserializeStruct, deserializeList, deserializeMap, field, SerializationException, DeserializationException, asSdkSerializable,
            serializeStruct, serializeList, serializeMap
        )

        object SerdeJson {
            val JsonSerialName = runtimeSymbol("JsonSerialName", KotlinDependency.CLIENT_RT_SERDE_JSON)
            val JsonSerializer = runtimeSymbol("JsonSerializer", KotlinDependency.CLIENT_RT_SERDE_JSON)
            val JsonDeserializer = runtimeSymbol("JsonDeserializer", KotlinDependency.CLIENT_RT_SERDE_JSON)
        }

        object SerdeXml {
            val XmlSerialName = runtimeSymbol("XmlSerialName", KotlinDependency.CLIENT_RT_SERDE_XML)
            val XmlCollectionName = runtimeSymbol("XmlCollectionName", KotlinDependency.CLIENT_RT_SERDE_XML)
            val XmlNamespace = runtimeSymbol("XmlNamespace", KotlinDependency.CLIENT_RT_SERDE_XML)
            val XmlCollectionValueNamespace = runtimeSymbol("XmlCollectionValueNamespace", KotlinDependency.CLIENT_RT_SERDE_XML)
            val XmlMapKeyNamespace = runtimeSymbol("XmlMapKeyNamespace", KotlinDependency.CLIENT_RT_SERDE_XML)
            val Flattened = runtimeSymbol("Flattened", KotlinDependency.CLIENT_RT_SERDE_XML)
            val XmlAttribute = runtimeSymbol("XmlAttribute", KotlinDependency.CLIENT_RT_SERDE_XML)
            val XmlMapName = runtimeSymbol("XmlMapName", KotlinDependency.CLIENT_RT_SERDE_XML)
            val XmlError = runtimeSymbol("XmlError", KotlinDependency.CLIENT_RT_SERDE_XML)
            val XmlSerializer = runtimeSymbol("XmlSerializer", KotlinDependency.CLIENT_RT_SERDE_XML)
            val XmlDeserializer = runtimeSymbol("XmlDeserializer", KotlinDependency.CLIENT_RT_SERDE_XML)
        }

        object SerdeFormUrl {
            val FormUrlSerialName = runtimeSymbol("FormUrlSerialName", KotlinDependency.CLIENT_RT_SERDE_FORM_URL)
            val FormUrlCollectionName = runtimeSymbol("FormUrlCollectionName", KotlinDependency.CLIENT_RT_SERDE_FORM_URL)
            val Flattened = runtimeSymbol("FormUrlFlattened", KotlinDependency.CLIENT_RT_SERDE_FORM_URL)
            val FormUrlMapName = runtimeSymbol("FormUrlMapName", KotlinDependency.CLIENT_RT_SERDE_FORM_URL)
            val QueryLiteral = runtimeSymbol("QueryLiteral", KotlinDependency.CLIENT_RT_SERDE_FORM_URL)
            val FormUrlSerializer = runtimeSymbol("FormUrlSerializer", KotlinDependency.CLIENT_RT_SERDE_FORM_URL)
        }
    }
}

private fun runtimeSymbol(name: String, dependency: KotlinDependency, subpackage: String = ""): Symbol = buildSymbol {
    this.name = name
    namespace(dependency, subpackage)
}
