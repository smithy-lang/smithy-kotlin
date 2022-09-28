/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.core

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.model.namespace
import software.amazon.smithy.kotlin.codegen.model.toSymbol

/**
 * Commonly used runtime types. Provides a single definition of a runtime symbol such that codegen isn't littered
 * with inline symbol creation which makes refactoring of the runtime more difficult and error prone.
 *
 * NOTE: Not all symbols need be added here but it doesn't hurt to define runtime symbols once.
 */
object RuntimeTypes {
    object Http {
        val HttpBody = runtimeSymbol("HttpBody", KotlinDependency.HTTP)
        val HttpMethod = runtimeSymbol("HttpMethod", KotlinDependency.HTTP)
        val SdkHttpClient = runtimeSymbol("SdkHttpClient", KotlinDependency.HTTP)
        val SdkHttpClientFn = runtimeSymbol("sdkHttpClient", KotlinDependency.HTTP)
        val ByteArrayContent = runtimeSymbol("ByteArrayContent", KotlinDependency.HTTP, "content")
        val QueryParameters = runtimeSymbol("QueryParameters", KotlinDependency.HTTP)
        val QueryParametersBuilder = runtimeSymbol("QueryParametersBuilder", KotlinDependency.HTTP)
        val toQueryParameters = runtimeSymbol("toQueryParameters", KotlinDependency.HTTP)
        val readAll = runtimeSymbol("readAll", KotlinDependency.HTTP)
        val parameters = runtimeSymbol("parameters", KotlinDependency.HTTP)
        val toByteStream = runtimeSymbol("toByteStream", KotlinDependency.HTTP)
        val toHttpBody = runtimeSymbol("toHttpBody", KotlinDependency.HTTP)
        val isSuccess = runtimeSymbol("isSuccess", KotlinDependency.HTTP)
        val StatusCode = runtimeSymbol("HttpStatusCode", KotlinDependency.HTTP)
        val toSdkByteReadChannel = runtimeSymbol("toSdkByteReadChannel", KotlinDependency.HTTP)

        object Util {
            val encodeLabel = runtimeSymbol("encodeLabel", KotlinDependency.HTTP, "util")
            val splitAsQueryParameters = runtimeSymbol("splitAsQueryParameters", KotlinDependency.HTTP, "util")
            val quoteHeaderValue = runtimeSymbol("quoteHeaderValue", KotlinDependency.HTTP, "util")
        }

        object Request {
            val HttpRequest = runtimeSymbol("HttpRequest", KotlinDependency.HTTP, "request")
            val HttpRequestBuilder = runtimeSymbol("HttpRequestBuilder", KotlinDependency.HTTP, "request")
            val url = runtimeSymbol("url", KotlinDependency.HTTP, "request")
            val headers = runtimeSymbol("headers", KotlinDependency.HTTP, "request")
        }

        object Response {
            val HttpCall = runtimeSymbol("HttpCall", KotlinDependency.HTTP, "response")
            val HttpResponse = runtimeSymbol("HttpResponse", KotlinDependency.HTTP, "response")
        }

        object Middlware {
            val Md5ChecksumMiddleware = runtimeSymbol("Md5Checksum", KotlinDependency.HTTP, "middleware")
            val MutateHeadersMiddleware = runtimeSymbol("MutateHeaders", KotlinDependency.HTTP, "middleware")
            val Retry = runtimeSymbol("Retry", KotlinDependency.HTTP, "middleware")
            val ResolveEndpoint = runtimeSymbol("ResolveEndpoint", KotlinDependency.HTTP, "middleware")
        }

        object Operation {
            val HttpDeserialize = runtimeSymbol("HttpDeserialize", KotlinDependency.HTTP, "operation")
            val HttpSerialize = runtimeSymbol("HttpSerialize", KotlinDependency.HTTP, "operation")
            val SdkHttpOperation = runtimeSymbol("SdkHttpOperation", KotlinDependency.HTTP, "operation")
            val OperationRequest = runtimeSymbol("OperationRequest", KotlinDependency.HTTP, "operation")
            val context = runtimeSymbol("context", KotlinDependency.HTTP, "operation")
            val roundTrip = runtimeSymbol("roundTrip", KotlinDependency.HTTP, "operation")
            val sdkRequestId = runtimeSymbol("sdkRequestId", KotlinDependency.HTTP, "operation")
            val execute = runtimeSymbol("execute", KotlinDependency.HTTP, "operation")
        }

        object Endpoints {
            val EndpointResolver = runtimeSymbol("EndpointResolver", KotlinDependency.HTTP, "endpoints")
        }

        object Engine {
            val HttpClientEngine = runtimeSymbol("HttpClientEngine", KotlinDependency.HTTP, "engine")
            val HttpClientEngineConfig = runtimeSymbol("HttpClientEngineConfig", KotlinDependency.HTTP, "engine")
        }
    }

    object Core {
        val IdempotencyTokenProviderExt = runtimeSymbol("idempotencyTokenProvider", KotlinDependency.CORE, "client")
        val ExecutionContext = runtimeSymbol("ExecutionContext", KotlinDependency.CORE, "client")
        val ErrorMetadata = runtimeSymbol("ErrorMetadata", KotlinDependency.CORE)
        val ServiceErrorMetadata = runtimeSymbol("ServiceErrorMetadata", KotlinDependency.CORE)
        val Instant = runtimeSymbol("Instant", KotlinDependency.CORE, "time")
        val TimestampFormat = runtimeSymbol("TimestampFormat", KotlinDependency.CORE, "time")
        val ClientException = runtimeSymbol("ClientException", KotlinDependency.CORE)

        object Content {
            val ByteArrayContent = runtimeSymbol("ByteArrayContent", KotlinDependency.CORE, "content")
            val ByteStream = runtimeSymbol("ByteStream", KotlinDependency.CORE, "content")
            val StringContent = runtimeSymbol("StringContent", KotlinDependency.CORE, "content")
            val toByteArray = runtimeSymbol("toByteArray", KotlinDependency.CORE, "content")
            val decodeToString = runtimeSymbol("decodeToString", KotlinDependency.CORE, "content")
        }

        object Retries {
            val Outcome = runtimeSymbol("Outcome", KotlinDependency.CORE, "retries")
            val RetryStrategy = runtimeSymbol("RetryStrategy", KotlinDependency.CORE, "retries")
            val StandardRetryStrategy = runtimeSymbol("StandardRetryStrategy", KotlinDependency.CORE, "retries")
            val StandardRetryStrategyOptions = runtimeSymbol("StandardRetryStrategyOptions", KotlinDependency.CORE, "retries")

            object Delay {
                val ExponentialBackoffWithJitter = runtimeSymbol("ExponentialBackoffWithJitter", KotlinDependency.CORE, "retries.delay")
                val ExponentialBackoffWithJitterOptions = runtimeSymbol("ExponentialBackoffWithJitterOptions", KotlinDependency.CORE, "retries.delay")
                val InfiniteTokenBucket = runtimeSymbol("InfiniteTokenBucket", KotlinDependency.CORE, "retries.delay")
                val StandardRetryTokenBucket = runtimeSymbol("StandardRetryTokenBucket", KotlinDependency.CORE, "retries.delay")
                val StandardRetryTokenBucketOptions = runtimeSymbol("StandardRetryTokenBucketOptions", KotlinDependency.CORE, "retries.delay")
            }

            object Policy {
                val Acceptor = runtimeSymbol("Acceptor", KotlinDependency.CORE, "retries.policy")
                val AcceptorRetryPolicy = runtimeSymbol("AcceptorRetryPolicy", KotlinDependency.CORE, "retries.policy")
                val ErrorTypeAcceptor = runtimeSymbol("ErrorTypeAcceptor", KotlinDependency.CORE, "retries.policy")
                val InputOutputAcceptor = runtimeSymbol("InputOutputAcceptor", KotlinDependency.CORE, "retries.policy")
                val OutputAcceptor = runtimeSymbol("OutputAcceptor", KotlinDependency.CORE, "retries.policy")
                val RetryDirective = runtimeSymbol("RetryDirective", KotlinDependency.CORE, "retries.policy")
                val RetryErrorType = runtimeSymbol("RetryErrorType", KotlinDependency.CORE, "retries.policy")
                val StandardRetryPolicy = runtimeSymbol("StandardRetryPolicy", KotlinDependency.CORE, "retries.policy")
                val SuccessAcceptor = runtimeSymbol("SuccessAcceptor", KotlinDependency.CORE, "retries.policy")
            }
        }

        object Smithy {
            val Document = runtimeSymbol("Document", KotlinDependency.CORE, "smithy")
            val buildDocument = runtimeSymbol("buildDocument", KotlinDependency.CORE, "smithy")
        }
    }

    object Hashing {
        val Sha256 = runtimeSymbol("Sha256", KotlinDependency.HASHING)
    }

    object Utils {
        val AttributeKey = runtimeSymbol("AttributeKey", KotlinDependency.UTILS)
        val flattenIfPossible = runtimeSymbol("flattenIfPossible", KotlinDependency.UTILS)
        val length = runtimeSymbol("length", KotlinDependency.UTILS)
        val truthiness = runtimeSymbol("truthiness", KotlinDependency.UTILS)
        val urlEncodeComponent = runtimeSymbol("urlEncodeComponent", KotlinDependency.UTILS, "text")
    }

    object Serde {
        val Serializer = runtimeSymbol("Serializer", KotlinDependency.SERDE)
        val Deserializer = runtimeSymbol("Deserializer", KotlinDependency.SERDE)
        val SdkFieldDescriptor = runtimeSymbol("SdkFieldDescriptor", KotlinDependency.SERDE)
        val SdkObjectDescriptor = runtimeSymbol("SdkObjectDescriptor", KotlinDependency.SERDE)
        val SerialKind = runtimeSymbol("SerialKind", KotlinDependency.SERDE)
        val SerializationException = runtimeSymbol("SerializationException", KotlinDependency.SERDE)
        val DeserializationException = runtimeSymbol("DeserializationException", KotlinDependency.SERDE)

        val serializeStruct = runtimeSymbol("serializeStruct", KotlinDependency.SERDE)
        val serializeList = runtimeSymbol("serializeList", KotlinDependency.SERDE)
        val serializeMap = runtimeSymbol("serializeMap", KotlinDependency.SERDE)

        val deserializeStruct = runtimeSymbol("deserializeStruct", KotlinDependency.SERDE)
        val deserializeList = runtimeSymbol("deserializeList", KotlinDependency.SERDE)
        val deserializeMap = runtimeSymbol("deserializeMap", KotlinDependency.SERDE)
        val asSdkSerializable = runtimeSymbol("asSdkSerializable", KotlinDependency.SERDE)
        val field = runtimeSymbol("field", KotlinDependency.SERDE)

        object SerdeJson {
            val JsonSerialName = runtimeSymbol("JsonSerialName", KotlinDependency.SERDE_JSON)
            val JsonSerializer = runtimeSymbol("JsonSerializer", KotlinDependency.SERDE_JSON)
            val JsonDeserializer = runtimeSymbol("JsonDeserializer", KotlinDependency.SERDE_JSON)
        }

        object SerdeXml {
            val XmlSerialName = runtimeSymbol("XmlSerialName", KotlinDependency.SERDE_XML)
            val XmlAliasName = runtimeSymbol("XmlAliasName", KotlinDependency.SERDE_XML)
            val XmlCollectionName = runtimeSymbol("XmlCollectionName", KotlinDependency.SERDE_XML)
            val XmlNamespace = runtimeSymbol("XmlNamespace", KotlinDependency.SERDE_XML)
            val XmlCollectionValueNamespace = runtimeSymbol("XmlCollectionValueNamespace", KotlinDependency.SERDE_XML)
            val XmlMapKeyNamespace = runtimeSymbol("XmlMapKeyNamespace", KotlinDependency.SERDE_XML)
            val Flattened = runtimeSymbol("Flattened", KotlinDependency.SERDE_XML)
            val XmlAttribute = runtimeSymbol("XmlAttribute", KotlinDependency.SERDE_XML)
            val XmlMapName = runtimeSymbol("XmlMapName", KotlinDependency.SERDE_XML)
            val XmlError = runtimeSymbol("XmlError", KotlinDependency.SERDE_XML)
            val XmlSerializer = runtimeSymbol("XmlSerializer", KotlinDependency.SERDE_XML)
            val XmlDeserializer = runtimeSymbol("XmlDeserializer", KotlinDependency.SERDE_XML)
        }

        object SerdeFormUrl {
            val FormUrlSerialName = runtimeSymbol("FormUrlSerialName", KotlinDependency.SERDE_FORM_URL)
            val FormUrlCollectionName = runtimeSymbol("FormUrlCollectionName", KotlinDependency.SERDE_FORM_URL)
            val Flattened = runtimeSymbol("FormUrlFlattened", KotlinDependency.SERDE_FORM_URL)
            val FormUrlMapName = runtimeSymbol("FormUrlMapName", KotlinDependency.SERDE_FORM_URL)
            val QueryLiteral = runtimeSymbol("QueryLiteral", KotlinDependency.SERDE_FORM_URL)
            val FormUrlSerializer = runtimeSymbol("FormUrlSerializer", KotlinDependency.SERDE_FORM_URL)
        }
    }

    object Auth {
        object Credentials {
            object AwsCredentials {
                val Credentials = runtimeSymbol("Credentials", KotlinDependency.AWS_CREDENTIALS)
                val CredentialsProvider = runtimeSymbol("CredentialsProvider", KotlinDependency.AWS_CREDENTIALS)
            }
        }

        object Signing {
            object AwsSigningCommon {
                val AwsSignedBodyHeader = runtimeSymbol("AwsSignedBodyHeader", KotlinDependency.AWS_SIGNING_COMMON)
                val AwsSigner = runtimeSymbol("AwsSigner", KotlinDependency.AWS_SIGNING_COMMON)
                val AwsSigningAttributes = runtimeSymbol("AwsSigningAttributes", KotlinDependency.AWS_SIGNING_COMMON)
                val AwsSigningMiddleware = runtimeSymbol("AwsSigningMiddleware", KotlinDependency.AWS_SIGNING_COMMON, "middleware")
                val HashSpecification = runtimeSymbol("HashSpecification", KotlinDependency.AWS_SIGNING_COMMON)
                val createPresignedRequest = runtimeSymbol("createPresignedRequest", KotlinDependency.AWS_SIGNING_COMMON)
                val PresignedRequestConfig = runtimeSymbol("PresignedRequestConfig", KotlinDependency.AWS_SIGNING_COMMON)
                val PresigningLocation = runtimeSymbol("PresigningLocation", KotlinDependency.AWS_SIGNING_COMMON)
                val ServicePresignConfig = runtimeSymbol("ServicePresignConfig", KotlinDependency.AWS_SIGNING_COMMON)
                val SigningEndpointProvider = runtimeSymbol("SigningEndpointProvider", KotlinDependency.AWS_SIGNING_COMMON)
            }

            object AwsSigningStandard {
                val DefaultAwsSigner = runtimeSymbol("DefaultAwsSigner", KotlinDependency.AWS_SIGNING_DEFAULT)
            }
        }
    }

    object IO {
        val Closeable = runtimeSymbol("Closeable", KotlinDependency.IO)
    }

    object Tracing {
        object Core {
            val DefaultTracer = runtimeSymbol("DefaultTracer", KotlinDependency.CORE, "tracing")
            val NoOpTraceProbe = runtimeSymbol("NoOpTraceProbe", KotlinDependency.CORE, "tracing")
            val TraceProbe = runtimeSymbol("TraceProbe", KotlinDependency.CORE, "tracing")
            val withChildSpan = runtimeSymbol("withChildSpan", KotlinDependency.CORE, "tracing")
        }
    }

    object KotlinxCoroutines {
        object Flow {
            // NOTE: smithy-kotlin core has an API dependency on this already
            val Flow = "kotlinx.coroutines.flow.Flow".toSymbol()
            val map = "kotlinx.coroutines.flow.map".toSymbol()
        }
    }
}

private fun runtimeSymbol(name: String, dependency: KotlinDependency, subpackage: String = ""): Symbol = buildSymbol {
    this.name = name
    namespace(dependency, subpackage)
}
