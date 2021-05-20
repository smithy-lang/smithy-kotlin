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
        val HttpRequestBuilder = runtimeSymbol("HttpRequestBuilder", KotlinDependency.CLIENT_RT_HTTP, "request")
        val HttpResponse = runtimeSymbol("HttpResponse", KotlinDependency.CLIENT_RT_HTTP, "response")
        val HttpSerialize = runtimeSymbol("HttpSerialize", KotlinDependency.CLIENT_RT_HTTP, "operation")
        val HttpDeserialize = runtimeSymbol("HttpDeserialize", KotlinDependency.CLIENT_RT_HTTP, "operation")
        val ByteArrayContent = runtimeSymbol("ByteArrayContent", KotlinDependency.CLIENT_RT_HTTP, "content")
        val MutateHeadersMiddleware = runtimeSymbol("MutateHeaders", KotlinDependency.CLIENT_RT_HTTP, "middleware")
        val EncodeLabel = runtimeSymbol("encodeLabel", KotlinDependency.CLIENT_RT_HTTP, "util")
    }

    object Core {
        val IdempotencyTokenProviderExt = runtimeSymbol("idempotencyTokenProvider", KotlinDependency.CLIENT_RT_CORE, "client")
        val ExecutionContext = runtimeSymbol("ExecutionContext", KotlinDependency.CLIENT_RT_CORE, "client")
        val ErrorMetadata = runtimeSymbol("ErrorMetadata", KotlinDependency.CLIENT_RT_CORE)
        val ServiceErrorMetadata = runtimeSymbol("ServiceErrorMetadata", KotlinDependency.CLIENT_RT_CORE)
        val Instant = runtimeSymbol("Instant", KotlinDependency.CLIENT_RT_CORE, "time")
        val TimestampFormat = runtimeSymbol("TimestampFormat", KotlinDependency.CLIENT_RT_CORE, "time")
    }

    object Serde {
        val SerdeProvider = runtimeSymbol("SerdeProvider", KotlinDependency.CLIENT_RT_SERDE)
        val Serializer = runtimeSymbol("Serializer", KotlinDependency.CLIENT_RT_SERDE)
        val Deserializer = runtimeSymbol("Deserializer", KotlinDependency.CLIENT_RT_SERDE)

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
