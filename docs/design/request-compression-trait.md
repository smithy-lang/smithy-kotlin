# Request Compression Trait

* **Type**: Design
* **Author(s)**: Omar Perez

## Abstract
This document covers the design and implementation to support [request compression](https://smithy.io/2.0/spec/behavior-traits.html?highlight=requestcompression#requestcompression-trait). Request compression refers to compressing the payload of a request prior to sending it to a service reducing the overall payload size. Request compression is used to reduce the overall number of requests and the bandwidth required to send data to the service.
It assumes familiarity with the [Smithy IDL](https://smithy.io/2.0/index.html), [compression algorithms](https://en.wikipedia.org/wiki/Data_compression), [interceptors](interceptors.md), and [middleware](https://en.wikipedia.org/wiki/Middleware).

## Design

### Client Experience

Here is how a client might use/configure this:

Both of these examples achieve the same result.
```kotlin
val client = S3Client {
    region = "us-west-2"
    disableRequestCompression = true
    requestMinCompressionSizeBytes = 200
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
    compressionAlgorithms += CustomCompressionAlgorithm()
}

class CustomCompressionAlgorithm : CompressionAlgorithm {
    override val id: String = "custom"
    override suspend fun compress(stream: HttpBody): HttpBody {
        ...
    }
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

Will look in all 4 locations:
```kotlin
    val client = S3Client.fromEnvironment {
        region = "us-west-2"
    }
```

Will only look at explicit client config:
```kotlin
    val client = S3Client {
        region = "us-west-2"
        disableRequestCompression = true
    }
```

#### Disable Request Compression 
* Type: Boolean
* Default: False


| Description                  | Client configuration             | JVM system property                  | Environment variable                     | AWS profile key                      |
|------------------------------|----------------------------------|--------------------------------------|------------------------------------------|--------------------------------------|
| Enable/disable compression   | `disableRequestCompression`      | `aws.disableRequestCompression`      | `AWS_DISABLE_REQUEST_COMPRESSION`        | `disable_request_compression`        |

#### Minimum Request Compression Size Bytes 
* Type: Int
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
    compressionAlgorithms += ...
}
```

### The Trait
Below is an abbreviated example of the request compression trait in use:

```smithy
@requestCompression(
    encodings: ["gzip", "custom"]
)
operation ...
```

The SDK has its own list of supported compression algorithms as a list of `CompressionAlgorithm`s in the client config.
```kotlin
private val compressionAlgorithms: List<CompressionAlgorithm> = mutableListOf(
    GzipCompressionAlgorithm()
)
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
/**
 * Represents a compression algorithm to be used for compressing request payloads on qualifying operations
 */
public interface CompressionAlgorithm {
    /**
     * The ID of the compression algorithm
     */
    public val id: String
    
    /**
     * Compresses a payload
     */
    public suspend fun compress(stream: HttpBody): HttpBody
}
```

The `id` is used to match a supported `CompressionAlgorithm` to a requested compression algorithm from the trait. The `compress` function is used to compress HTTP bodies.

#### Compression
Gzip JVM compression implementation:

```kotlin
/**

Plan on writing code to convert SourceContent & ChannelContent to  
`java.io.OutputStream` to use `GZIPOutputStream` in similar fashion as above.
`GZIPOutputStream` takes in a `java.io.OutputStream` which `ByteArrayOutputStream` is.

Could also use function to convert stream to byte array but chances are will run out
of memory doing so. Body will probably be large if using streaming trait on it.

If that code to convert the types is not possible for some reason then will have to 
handwrite gzip implementation.
 
**/
```

### Codegen
Middleware registration has the following steps:

* A Kotlin integration will look at a service model
* Determine if the service has an operation with the `requestCompression` trait
* If so middleware is registered  for the service
* The middleware will look at each operation in the service individually
* The middleware will then determine if an operation has the `requestCompression` trait
* If so then it'll add an interceptor to the operation

```kotlin
/**
 * Middleware that enables compression of payloads for HTTP requests
 */
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

    // TODO: Use `additionalServiceConfigProps` to register `compressionAlgorithms`
    // TODO: Register function for client config to support user supplied compression algorithms (`addCompressionAlgorithms`)

    // Middleware
    private val requestCompressionTraitMiddleware = object : ProtocolMiddleware {
        private val interceptorSymbol = RuntimeTypes.HttpClient.Interceptors.RequestCompressionTraitInterceptor
        override val name: String = "RequestCompressionTrait"

        // Will add interceptor to operation(s) in service with `requestCompression` trait 
        override fun render(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
            op.getTrait<RequestCompressionTrait>()?.let { trait ->
                val requestedCompressionAlgorithms = trait.encodings

                writer.withBlock(
                    "if (config.disableRequestCompression == false) {",
                    "}"
                ) {
                    withBlock(
                            "op.interceptors.add(#T(",
                            "))",
                            interceptorSymbol,
                    ) {
                        write("config.requestMinCompressionSizeBytes,")
                        write("listOf(${requestedCompressionAlgorithms.joinToString(", ")}),")
                        // write("config.compressionAlgorithms")
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
if (!config.disableRequestCompression) {
    op.interceptors.add(RequestCompressionTraitInterceptor(
        config.requestMinCompressionSizeBytes,
        listOf("gzip", "custom", ),
        // config.compressionAlgorithms
    )
}
...
```

### Implementation
This feature is implemented by registering an interceptor that replaces the outgoing request body with a compressed equivalent.

The interceptor holds most of the logic for this feature to work. The mapping logic for requested to supported compression algorithms is inside it. The actual compression is initiated here and the header logic as well.
```kotlin
private val VALID_COMPRESSION_RANGE = 0..10485760
private val DEFAULT_COMPRESSION_THRESHOLD_BYTES = 10240

/**
 * HTTP interceptor that compresses operation request payloads when eligible
 */
public class RequestCompressionTraitInterceptor(
    private val compressionThreshold: Int = DEFAULT_COMPRESSION_THRESHOLD_BYTES,
    private val requestedCompressionAlgorithms: List<String>,
    private val supportedCompressionAlgorithms: List<CompressionAlgorithm>,
) : HttpInterceptor {
    
    // Verify min compression size setting is in range
    init {
        require(compressionThreshold in VALID_COMPRESSION_RANGE) { "compressionThresholdBytes ($compressionThresholdBytes) must be in the range $VALID_COMPRESSION_RANGE" }
    }

    override suspend fun modifyBeforeRetryLoop(
        context: ProtocolRequestInterceptorContext<Any, HttpRequest>
    ): HttpRequest {
        
        // Determine if going forward with compression
        val payloadSize = context.protocolRequest.body.contentLength
        val streamingPayload = payloadSize == null
        if (streamingPayload || payloadSize!! >= compressionThreshold) {

            // Check if requested algorithm(s) is supported
            supportedCompressionAlgorithms.find { supported ->
                requestedCompressionAlgorithms.find { supported.id == it } != null
            }?.let { algorithm ->

                // Attempt compression
                val request = context.protocolRequest.toBuilder()
                request.body = request.body
                // TODO: Write compression
                request.headers.append("Content-Encoding", algorithm.id)
                return request.build()
            }
        }
        return context.protocolRequest
    }
}
```

### Testing
Testing is done using both unit tests and Smithy HTTP protocol compliance tests. The SDK unit tests are used to verify behavior and ensure it doesn't drift over time. Smithy HTTP protocol tests are used to test the behavior in a more integrated way.

## References
[Smithy request compression trait](https://smithy.io/2.0/spec/behavior-traits.html?highlight=requestcompression#requestcompression-trait)

[Smithy IDL Docs](https://smithy.io/2.0/index.html)

[Smithy HTTP protocol compliance test for `requestCompression`](https://github.com/smithy-lang/smithy/blob/806b624733e3fc0e2d3a866ff986d5a63bb96274/smithy-aws-protocol-tests/model/awsJson1_0/requestCompression.smithy)

[Shared  `config` and `credentials` file docs](https://docs.aws.amazon.com/sdkref/latest/guide/file-format.html)

[Docs for Kotlin multi-platform (expect/actual)](https://kotlinlang.org/docs/multiplatform-expect-actual.html)

## Revision history
11/07/2023 - Created