# Request Compression Trait

* **Type**: Design
* **Author(s)**: Omar Perez

## Abstract
This document covers the design and implementation to support [request compression](https://smithy.io/2.0/spec/behavior-traits.html?highlight=requestcompression#requestcompression-trait). Request compression refers to compressing the payload of a request prior to sending it to a service reducing the overall payload size. Request compression is used to reduce the overall number of requests and the bandwidth required to send data to the service.
It assumes familiarity with the [Smithy IDL](https://smithy.io/2.0/index.html), [compression algorithms](https://en.wikipedia.org/wiki/Data_compression), [interceptors](interceptors.md), and [middleware](https://en.wikipedia.org/wiki/Middleware).

## Design

### Client Experience

Both of these examples achieve the same result.
```kotlin
val client = S3Client {
    region = "us-west-2"
    requestCompression {
        requestMinCompressionSizeBytes = 200
        disableRequestCompression = true
    }
}
```

```kotlin
val client = S3Client.fromEnvironment {
    region = "us-west-2"
}

// In config file
[default]
disable_request_compression = true
request_min_compression_size_bytes = 200
```

This would add a user supplied compression algorithm.
```kotlin
val client = S3Client.fromEnvironment {
    region = "us-west-2"
    requestCompression {
        compressionAlgorithms += CustomCompressionAlgorithm()
    }
}

class CustomCompressionAlgorithm : CompressionAlgorithm {
    override val id: String = "custom"
    ...
}
```

### Client Configuration

This feature can be configured by a customer to adjust the minimum payload size threshold used to determine when a request should be compressed or be disabled completely. These settings are sourced from the following locations in priority order:

1. Explicit client configuration
2. JVM system properties
3. Environment variables
4. AWS profile

*NOTE*: The client needs to be instantiated using `fromEnvironment()` for it to check all the available locations or else it
will only look at the explicit client config.

#### Disable Request Compression 
* Type: Boolean
* Default: False


| Description                  | Client configuration             | JVM system property                  | Environment variable                     | AWS profile key                      |
|------------------------------|----------------------------------|--------------------------------------|------------------------------------------|--------------------------------------|
| Enable/disable compression   | `disableRequestCompression`      | `aws.disableRequestCompression`      | `AWS_DISABLE_REQUEST_COMPRESSION`        | `disable_request_compression`        |

#### Minimum Request Compression Size Bytes 
* Type: Long
* Default: 10,240 (10KB)
* Range: 0 - 10,485,760 (0MB - 10MB) 
* Notes: Setting will be ignored if payload is a stream

| Description                  | Client configuration             | JVM system property                  | Environment variable                     | AWS profile key                      |
|------------------------------|----------------------------------|--------------------------------------|------------------------------------------|--------------------------------------|
| Min payload size to compress | `requestMinCompressionSizeBytes` | `aws.requestMinCompressionSizeBytes` | `AWS_REQUEST_MIN_COMPRESSION_SIZE_BYTES` | `request_min_compression_size_bytes` |

This feature can also be configured to use user supplied compression algorithms. The user can add compression algorithms using the client builder. A user supplied algorithm may override the built-in implementation, and the compression algorithm must implement the `CompressionAlgorithm` interface.
```kotlin
val client = S3Client.fromEnvironment {
    region = "us-west-2"
    requestCompression {
        compressionAlgorithms = mutableListOf(CustomGzip())
    }
}

class CustomGzip : CompressionAlgorithm {
    override val id: String = "gzip"
    ...
}
```

### Behavior
Each algorithm will be attempted in the order they appear in the trait until one that is supported by the runtime is found. If no algorithm is found the request will continue without compression applied.

After compression occurs, the `Content-Encoding` header will contain the name of the compression algorithm used. It will be appended to the list of content encodings if the header was already present with some value.

No encoding header before compression:
```http request
POST /upload-data HTTP/1.1
Host: example.com
Content-Type: application/json
Content-Encoding: gzip
```

Existing `br` encoding in header before compression:
```http request
POST /upload-data HTTP/1.1
Host: example.com
Content-Type: application/json
Content-Encoding: br, gzip 
```

### Compression Algorithms
Given that Kotlin is multiplatform there should be multiple implementations of each compression algorithm. Currently gzip is the only supported compression algorithm by the server side and the only one supported for
the SDK. Only gzip for JVM is supported specifically. This is done by using the following classes from the Java standard library: `GZIPInputStream` and `GZIPOutputStream`. The other KMP targets are not implemented, and are planned to be implemented using the CRT.

New algorithms can be added as they become supported using the compression algorithm interface and by adding them to the list of supported algorithms. The same goes for non JVM KMP targets, these can be added by filling in the `actual` declarations for the target.

#### Interfaces
This interface is used to represent a compression algorithm.

```kotlin
public interface CompressionAlgorithm {
    /**
     * The ID of the compression algorithm
     */
    public val id: String

    /**
     * The name of the content encoding to be appended to the `Content-Encoding` header.
     * The [IANA](https://www.iana.org/assignments/http-parameters/http-parameters.xhtml)
     * has a list of registered encodings for reference.
     */
    public val contentEncoding: String
    
    /**
     * Compresses a stream
     */
    public fun compress(stream: ByteStream): ByteStream
}
```

The `id` is used to match a supported `CompressionAlgorithm` to a requested compression algorithm from the trait. The `contentEncoding` is used to set a value in the `Content-Encoding` header, and the `compress` function is used to compress the request.

#### Compression

ByteStream implementation
```kotlin
val byteArrayOutputStream = ByteArrayOutputStream()
val gzipOutputStream = GZIPOutputStream(byteArrayOutputStream)

gzipOutputStream.write(stream.bytes())
gzipOutputStream.close()

val compressedBody = byteArrayOutputStream.toByteArray()
byteArrayOutputStream.close()

return compressedBody
```

The `GzipSdkSource` and `GzipByteReadChannel` implementations wrap the source so that it compresses into gzip format with each read.

### Codegen
Middleware registration has the following steps:

* A Kotlin integration will look at a service model
* Determine if the service has an operation with the `requestCompression` trait
* If so middleware is registered  for the service
* The middleware will look at each operation in the service individually
* The middleware will then determine if an operation has the `requestCompression` trait and request compression is not disabled.
* If so then it'll add an interceptor to the operation

```kotlin
class RequestCompressionTrait : KotlinIntegration {

    // Will determine if middleware is registered for service
    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean = model
        .shapes<OperationShape>()
        .any { it.hasTrait<RequestCompressionTrait>() }

    // Registers middleware
    override fun customizeMiddleware(
        ctx: ProtocolGenerator.GenerationContext,
        resolved: List<ProtocolMiddleware>
    ): List<ProtocolMiddleware> = resolved + requestCompressionTraitMiddleware

    // Adds request compression config
    override fun additionalServiceConfigProps(ctx: CodegenContext): List<ConfigProperty> =
        listOf(
            ConfigProperty {
                name = "requestCompression"
                symbol = RuntimeTypes.SmithyClient.Config.RequestCompressionConfig
                builderSymbol = RuntimeTypes.SmithyClient.Config.RequestCompressionConfig.nestedBuilder.toBuilder()
                    .defaultValue("${this.symbol}{}.toBuilderApplicator()")
                    .nonNullable()
                    .build()
                toBuilderExpression = ".toBuilderApplicator()"
                baseClass = RuntimeTypes.SmithyClient.Config.CompressionClientConfig
                builderBaseClass = RuntimeTypes.SmithyClient.Config.CompressionClientConfig.nestedBuilder
                propertyType = ConfigPropertyType.Custom(
                    render = { prop, writer ->
                        writer.write(
                            "override val #1L: #2T = #2T(builder.#1L)",
                            prop.propertyName,
                            prop.symbol,
                        )
                    },
                )
                documentation = """
                The configuration properties for request compression:
                
                * compressionAlgorithms:
                
                 The list of compression algorithms supported by the client.
                 More compression algorithms can be added and may override an existing implementation.
                 Use the `CompressionAlgorithm` interface to create one.
                
                * disableRequestCompression:
                 
                 Flag used to determine when a request should be compressed or not.
                 False by default.
                             
                * requestMinCompressionSizeBytes:
                 
                The threshold in bytes used to determine if a request should be compressed or not.
                MUST be in the range 0-10,485,760 (10 MB). Defaults to 10,240 (10 KB).
                """.trimIndent()
            },
        )

    // Middleware
    private val requestCompressionTraitMiddleware = object : ProtocolMiddleware {
        override val name: String = "RequestCompressionTrait"

        override fun isEnabledFor(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Boolean =
            op.hasTrait<RequestCompressionTrait>()

        override fun render(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
            val requestCompressionTrait = op.getTrait<RequestCompressionTrait>()!!
            val supportedCompressionAlgorithms = requestCompressionTrait.encodings

            writer.withBlock(
                "if (config.requestCompression.disableRequestCompression == false) {",
                "}",
            ) {
                withBlock(
                    "op.interceptors.add(#T(",
                    "))",
                    RuntimeTypes.HttpClient.Interceptors.RequestCompressionInterceptor,
                ) {
                    write("config.requestCompression.requestMinCompressionSizeBytes,")
                    write("config.requestCompression.compressionAlgorithms,")
                    write(
                        "listOf(${supportedCompressionAlgorithms.joinToString(
                            separator = ", ",
                            transform = {
                                "\"$it\""
                            },
                        )}),",
                    )
                }
            }
        }
    }
    }
}
```

Interceptor registered on an operation:
```kotlin
...
if (config.requestCompression.disableRequestCompression == false) {
    op.interceptors.add(RequestCompressionTraitInterceptor(
        config.requestCompression.requestMinCompressionSizeBytes,
        config.requestCompression.compressionAlgorithms,
        listOf("gzip", "custom", ),
    )
}
...
```

### Implementation
This feature is implemented by registering an interceptor that replaces the outgoing request body with a compressed equivalent.

The interceptor holds the search logic for requested to supported compression algorithms, and the actual compression is initiated here.
```kotlin
private val VALID_COMPRESSION_THRESHOLD_BYTES_RANGE = 0..10_485_760

@InternalApi
public class RequestCompressionInterceptor(
    private val compressionThresholdBytes: Long,
    private val availableCompressionAlgorithms: List<CompressionAlgorithm>,
    private val supportedCompressionAlgorithms: List<String>,
) : HttpInterceptor {

    init {
        require(compressionThresholdBytes in VALID_COMPRESSION_THRESHOLD_BYTES_RANGE) { "compressionThresholdBytes ($compressionThresholdBytes) must be in the range $VALID_COMPRESSION_THRESHOLD_BYTES_RANGE" }
    }

    override suspend fun modifyBeforeRetryLoop(
        context: ProtocolRequestInterceptorContext<Any, HttpRequest>,
    ): HttpRequest {
        val payloadSizeBytes = context.protocolRequest.body.contentLength
        
        // Searching compression algorithms
        val algorithm = supportedCompressionAlgorithms.firstNotNullOfOrNull { id ->
            availableCompressionAlgorithms.find { it.id == id }
        }

        // Determining if compression is applied or not
        return if (algorithm != null && (context.protocolRequest.body.isStreaming || payloadSizeBytes?.let { it >= compressionThresholdBytes } == true)) {
            algorithm.compressRequest(context.protocolRequest)
        } else {
            val logger = coroutineContext.logger<RequestCompressionInterceptor>()
            val skipCause = if (algorithm == null) "no modeled compression algorithms are supported by the client" else "request size threshold ($compressionThresholdBytes) was not met"

            logger.debug { "skipping request compression because $skipCause" }

            context.protocolRequest
        }
    }

    /**
     * Determines if a http body is streaming type or not.
     */
    private val HttpBody.isStreaming: Boolean
        get() = this is HttpBody.ChannelContent || this is HttpBody.SourceContent || this.contentLength == null
}
```

The Http Request is compressed and the `Content-Encoding` header is appended from this function.
```kotlin
public fun CompressionAlgorithm.compressRequest(request: HttpRequest): HttpRequest {
    val stream = request.body.toByteStream() ?: return request

    val compressedRequest = request.toBuilder()

    val compressedStream = compress(stream)
    compressedRequest.body = compressedStream.toHttpBody()

    compressedRequest.headers.append("Content-Encoding", contentEncoding)

    return compressedRequest.build()
}
```

### Testing
Testing is done using both unit tests and Smithy HTTP protocol compliance tests. The SDK unit tests are used to verify behavior and ensure it doesn't drift over time. Smithy HTTP protocol tests are used to test the behavior in a more integrated way.

## References
[Smithy request compression trait](https://smithy.io/2.0/spec/behavior-traits.html?highlight=requestcompression#requestcompression-trait)

[Smithy IDL Docs](https://smithy.io/2.0/index.html)

[Smithy HTTP protocol compliance test for `requestCompression`](https://github.com/smithy-lang/smithy/blob/806b624733e3fc0e2d3a866ff986d5a63bb96274/smithy-aws-protocol-tests/model/awsJson1_0/requestCompression.smithy)

[Shared `config` and `credentials` file docs](https://docs.aws.amazon.com/sdkref/latest/guide/file-format.html)

[Docs for Kotlin multi-platform (expect/actual)](https://kotlinlang.org/docs/multiplatform-expect-actual.html)

## Revision history
11/07/2023 - Created
12/15/2023 - Updated after implementation