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
 * with inline symbol creation which makes refactoring of the runtime more difficult and error-prone.
 */
object RuntimeTypes {
    object Http : RuntimeTypePackage(KotlinDependency.HTTP) {
        val HttpBody = symbol("HttpBody")
        val HttpMethod = symbol("HttpMethod")
        val SdkHttpClient = symbol("SdkHttpClient")
        val SdkHttpClientFn = symbol("sdkHttpClient")
        val ByteArrayContent = symbol("ByteArrayContent", subpackage = "content")
        val QueryParameters = symbol("QueryParameters")
        val QueryParametersBuilder = symbol("QueryParametersBuilder")
        val toQueryParameters = symbol("toQueryParameters")
        val readAll = symbol("readAll")
        val parameters = symbol("parameters")
        val toByteStream = symbol("toByteStream")
        val toHttpBody = symbol("toHttpBody")
        val isSuccess = symbol("isSuccess")
        val StatusCode = symbol("HttpStatusCode")
        val toSdkByteReadChannel = symbol("toSdkByteReadChannel")
        val Headers = symbol("Headers")
        val Url = symbol("Url")

        object Util : RuntimeTypePackage(KotlinDependency.HTTP, "util") {
            val encodeLabel = symbol("encodeLabel")
            val splitAsQueryParameters = symbol("splitAsQueryParameters")
            val quoteHeaderValue = symbol("quoteHeaderValue")
        }

        object Request : RuntimeTypePackage(KotlinDependency.HTTP, "request") {
            val HttpRequest = symbol("HttpRequest")
            val HttpRequestBuilder = symbol("HttpRequestBuilder")
            val url = symbol("url")
            val headers = symbol("headers")
            val toBuilder = symbol("toBuilder")
        }

        object Response : RuntimeTypePackage(KotlinDependency.HTTP, "response") {
            val HttpCall = symbol("HttpCall")
            val HttpResponse = symbol("HttpResponse")
        }

        object Middleware : RuntimeTypePackage(KotlinDependency.HTTP, "middleware") {
            val MutateHeadersMiddleware = symbol("MutateHeaders")
            val RetryMiddleware = symbol("RetryMiddleware")
            val ResolveEndpoint = symbol("ResolveEndpoint")
        }

        object Operation : RuntimeTypePackage(KotlinDependency.HTTP, "operation") {
            val HttpDeserialize = symbol("HttpDeserialize")
            val HttpSerialize = symbol("HttpSerialize")
            val SdkHttpOperation = symbol("SdkHttpOperation")
            val SdkHttpRequest = symbol("SdkHttpRequest")
            val OperationRequest = symbol("OperationRequest")
            val context = symbol("context")
            val roundTrip = symbol("roundTrip")
            val sdkRequestId = symbol("sdkRequestId")
            val execute = symbol("execute")
            val InlineMiddleware = symbol("InlineMiddleware")
        }

        object Endpoints : RuntimeTypePackage(KotlinDependency.HTTP, "endpoints") {
            val EndpointProvider = symbol("EndpointProvider")
            val Endpoint = symbol("Endpoint")
            val EndpointProviderException = symbol("EndpointProviderException")
            val setResolvedEndpoint = symbol("setResolvedEndpoint")

            object Functions : RuntimeTypePackage(KotlinDependency.HTTP, "endpoints.functions") {
                val substring = symbol("substring")
                val isValidHostLabel = symbol("isValidHostLabel")
                val uriEncode = symbol("uriEncode")
                val parseUrl = symbol("parseUrl")
                val Url = symbol("Url")
            }
        }

        object Config : RuntimeTypePackage(KotlinDependency.HTTP, "config") {
            val HttpClientConfig = symbol("HttpClientConfig")
        }
        object Engine : RuntimeTypePackage(KotlinDependency.HTTP, "engine") {
            val HttpClientEngine = symbol("HttpClientEngine")
            val manage = symbol("manage", "engine.internal", isExtension = true)
        }
        object Interceptors : RuntimeTypePackage(KotlinDependency.HTTP, "interceptors") {
            val HttpInterceptor = symbol("HttpInterceptor")
            val Md5ChecksumInterceptor = symbol("Md5ChecksumInterceptor")
            val FlexibleChecksumsRequestInterceptor = symbol("FlexibleChecksumsRequestInterceptor")
            val FlexibleChecksumsResponseInterceptor = symbol("FlexibleChecksumsResponseInterceptor")
        }
    }

    object Core : RuntimeTypePackage(KotlinDependency.CORE) {
        val ExecutionContext = symbol("ExecutionContext", "client")
        val ErrorMetadata = symbol("ErrorMetadata")
        val ServiceErrorMetadata = symbol("ServiceErrorMetadata")
        val Instant = symbol("Instant", "time")
        val TimestampFormat = symbol("TimestampFormat", "time")
        val ClientException = symbol("ClientException")

        object Content : RuntimeTypePackage(KotlinDependency.CORE, "content") {
            val ByteArrayContent = symbol("ByteArrayContent")
            val ByteStream = symbol("ByteStream")
            val StringContent = symbol("StringContent")
            val toByteArray = symbol("toByteArray")
            val decodeToString = symbol("decodeToString")
        }

        object Retries : RuntimeTypePackage(KotlinDependency.CORE, "retries") {
            val Outcome = symbol("Outcome")
            val RetryStrategy = symbol("RetryStrategy")
            val StandardRetryStrategy = symbol("StandardRetryStrategy")
            val StandardRetryStrategyOptions = symbol("StandardRetryStrategyOptions")

            object Delay : RuntimeTypePackage(KotlinDependency.CORE, "retries.delay") {
                val ExponentialBackoffWithJitter = symbol("ExponentialBackoffWithJitter")
                val ExponentialBackoffWithJitterOptions = symbol("ExponentialBackoffWithJitterOptions")
                val InfiniteTokenBucket = symbol("InfiniteTokenBucket")
                val StandardRetryTokenBucket = symbol("StandardRetryTokenBucket")
                val StandardRetryTokenBucketOptions = symbol("StandardRetryTokenBucketOptions")
            }

            object Policy : RuntimeTypePackage(KotlinDependency.CORE, "retries.policy") {
                val Acceptor = symbol("Acceptor")
                val AcceptorRetryPolicy = symbol("AcceptorRetryPolicy")
                val ErrorTypeAcceptor = symbol("ErrorTypeAcceptor")
                val InputOutputAcceptor = symbol("InputOutputAcceptor")
                val OutputAcceptor = symbol("OutputAcceptor")
                val RetryDirective = symbol("RetryDirective")
                val RetryErrorType = symbol("RetryErrorType")
                val RetryPolicy = symbol("RetryPolicy")
                val StandardRetryPolicy = symbol("StandardRetryPolicy")
                val SuccessAcceptor = symbol("SuccessAcceptor")
            }
        }

        object Smithy : RuntimeTypePackage(KotlinDependency.CORE, "smithy") {
            val Document = symbol("Document")
            val buildDocument = symbol("buildDocument")
        }

        object Client : RuntimeTypePackage(KotlinDependency.CORE, "client") {
            val SdkClient = symbol("SdkClient")
            val AbstractSdkClientBuilder = symbol("AbstractSdkClientBuilder")
            val SdkLogMode = symbol("SdkLogMode")
            val SdkClientConfig = symbol("SdkClientConfig")
            val SdkClientFactory = symbol("SdkClientFactory")
            val RequestInterceptorContext = symbol("RequestInterceptorContext")
            val ProtocolRequestInterceptorContext = symbol("ProtocolRequestInterceptorContext")
            val IdempotencyTokenProvider = symbol("IdempotencyTokenProvider")
            val IdempotencyTokenConfig = symbol("IdempotencyTokenConfig")
            val IdempotencyTokenProviderExt = symbol("idempotencyTokenProvider")
        }

        object Hashing : RuntimeTypePackage(KotlinDependency.CORE, "hashing") {
            val Sha256 = symbol("Sha256")
        }

        object IO : RuntimeTypePackage(KotlinDependency.CORE, "io") {
            val Closeable = symbol("Closeable")
            val SdkManagedGroup = symbol("SdkManagedGroup")
            val addIfManaged = symbol("addIfManaged", isExtension = true)
        }
        object Utils : RuntimeTypePackage(KotlinDependency.CORE, "util") {
            val Attributes = symbol("Attributes")
            val AttributeKey = symbol("AttributeKey")
            val flattenIfPossible = symbol("flattenIfPossible")
            val length = symbol("length")
            val truthiness = symbol("truthiness")
            val urlEncodeComponent = symbol("urlEncodeComponent", "text")
            val decodeBase64 = symbol("decodeBase64")
            val decodeBase64Bytes = symbol("decodeBase64Bytes")
            val encodeBase64 = symbol("encodeBase64")
            val encodeBase64String = symbol("encodeBase64String")
        }
    }

    object Serde : RuntimeTypePackage(KotlinDependency.SERDE) {
        val Serializer = symbol("Serializer")
        val Deserializer = symbol("Deserializer")
        val SdkFieldDescriptor = symbol("SdkFieldDescriptor")
        val SdkObjectDescriptor = symbol("SdkObjectDescriptor")
        val SerialKind = symbol("SerialKind")
        val SerializationException = symbol("SerializationException")
        val DeserializationException = symbol("DeserializationException")

        val serializeStruct = symbol("serializeStruct")
        val serializeList = symbol("serializeList")
        val serializeMap = symbol("serializeMap")

        val deserializeStruct = symbol("deserializeStruct")
        val deserializeList = symbol("deserializeList")
        val deserializeMap = symbol("deserializeMap")
        val asSdkSerializable = symbol("asSdkSerializable")
        val field = symbol("field")

        object SerdeJson : RuntimeTypePackage(KotlinDependency.SERDE_JSON) {
            val JsonSerialName = symbol("JsonSerialName")
            val JsonSerializer = symbol("JsonSerializer")
            val JsonDeserializer = symbol("JsonDeserializer")
        }

        object SerdeXml : RuntimeTypePackage(KotlinDependency.SERDE_XML) {
            val XmlSerialName = symbol("XmlSerialName")
            val XmlAliasName = symbol("XmlAliasName")
            val XmlCollectionName = symbol("XmlCollectionName")
            val XmlNamespace = symbol("XmlNamespace")
            val XmlCollectionValueNamespace = symbol("XmlCollectionValueNamespace")
            val XmlMapKeyNamespace = symbol("XmlMapKeyNamespace")
            val Flattened = symbol("Flattened")
            val XmlAttribute = symbol("XmlAttribute")
            val XmlMapName = symbol("XmlMapName")
            val XmlError = symbol("XmlError")
            val XmlSerializer = symbol("XmlSerializer")
            val XmlDeserializer = symbol("XmlDeserializer")
        }

        object SerdeFormUrl : RuntimeTypePackage(KotlinDependency.SERDE_FORM_URL) {
            val FormUrlSerialName = symbol("FormUrlSerialName")
            val FormUrlCollectionName = symbol("FormUrlCollectionName")
            val Flattened = symbol("FormUrlFlattened")
            val FormUrlMapName = symbol("FormUrlMapName")
            val QueryLiteral = symbol("QueryLiteral")
            val FormUrlSerializer = symbol("FormUrlSerializer")
        }
    }

    object Auth {
        object Credentials {
            object AwsCredentials : RuntimeTypePackage(KotlinDependency.AWS_CREDENTIALS) {
                val Credentials = symbol("Credentials")
                val CredentialsProvider = symbol("CredentialsProvider")
            }
        }

        object Signing {
            object AwsSigningCommon : RuntimeTypePackage(KotlinDependency.AWS_SIGNING_COMMON) {
                val AwsSignedBodyHeader = symbol("AwsSignedBodyHeader")
                val AwsSigner = symbol("AwsSigner")
                val AwsSigningAttributes = symbol("AwsSigningAttributes")
                val AwsHttpSigner = symbol("AwsHttpSigner")
                val HashSpecification = symbol("HashSpecification")
                val createPresignedRequest = symbol("createPresignedRequest")
                val PresignedRequestConfig = symbol("PresignedRequestConfig")
                val PresigningLocation = symbol("PresigningLocation")
                val ServicePresignConfig = symbol("ServicePresignConfig")
                val SigningEndpointProvider = symbol("SigningEndpointProvider")
                val SigningContextualizedEndpoint = symbol("SigningContextualizedEndpoint")
            }

            object AwsSigningStandard : RuntimeTypePackage(KotlinDependency.AWS_SIGNING_DEFAULT) {
                val DefaultAwsSigner = symbol("DefaultAwsSigner")
            }
        }
    }

    object Tracing {
        object Core : RuntimeTypePackage(KotlinDependency.TRACING_CORE) {
            val debug = symbol("debug")
            val DefaultTracer = symbol("DefaultTracer")
            val LoggingTraceProbe = symbol("LoggingTraceProbe")
            val TraceProbe = symbol("TraceProbe")
            val Tracer = symbol("Tracer")
            val TracingClientConfig = symbol("TracingClientConfig")
            val withRootTraceSpan = symbol("withRootTraceSpan")
        }
    }

    object KotlinCoroutines {
        val coroutineContext = "kotlin.coroutines.coroutineContext".toSymbol()
    }

    object KotlinxCoroutines {
        val CancellationException = "kotlinx.coroutines.CancellationException".toSymbol()

        object Flow {
            // NOTE: smithy-kotlin core has an API dependency on this already
            val Flow = "kotlinx.coroutines.flow.Flow".toSymbol()
            val map = "kotlinx.coroutines.flow.map".toSymbol()
        }
    }

    object HttpClientEngines {
        object Default : RuntimeTypePackage(KotlinDependency.DEFAULT_HTTP_ENGINE) {
            val DefaultHttpEngine = symbol("DefaultHttpEngine")
        }
    }
}

abstract class RuntimeTypePackage(
    val dependency: KotlinDependency,
    val defaultSubpackage: String = "",
) {
    /**
     * Create a symbol named by [name] from the [RuntimeTypePackage].
     * @param name the name of the symbol
     * @param subpackage the subpackage from the [dependency] namespace, defaults to [defaultSubpackage]
     */
    fun symbol(name: String, subpackage: String = defaultSubpackage, isExtension: Boolean = false): Symbol = buildSymbol {
        this.name = name
        namespace(dependency, subpackage)
        this.isExtension = isExtension
    }
}
