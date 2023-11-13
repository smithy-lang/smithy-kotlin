# Request Compression Trait

* **Type**: Design
* **Author(s)**: Omar Perez

## Abstract
This document covers the design and implementation to support request compression. Request compression refers to compressing the payload of a request prior to sending it to a service reducing the overall payload size. Request compression is used to reduce the overall number of requests and the bandwidth required to send data to the service.
It assumes familiarity with the Smithy IDL, compression algorithms, interceptors, and middleware.

## Design

### Client Experience
The client experience should be seamless.

Here is how a client might use/configure this:
```kotlin
val client = S3Client {
    region = "us-west-2"
    disableRequestCompression = true
    requestMinCompressionSizeBytes = 200
}
```
or
```kotlin
val client = S3Client.fromEnvironment {
    region = "us-west-2"
}
```
```kotlin
// In config file 
[default]
disable_request_compression = true
request_min_compression_size_bytes = 200
```
Both of these examples achieve the same result.

### Client Configuration

This feature can be configured by a customer to adjust the minimum payload size threshold used to determine when a request should be compressed or be disabled completely. These settings are sourced from the following locations in priority order:

1. Explicit client configuration
2. JVM system properties
3. Environment variables
4. AWS profile

```kotlin
val setting = locationOne ?: locationTwo ?: locationThree ?: locationFour 
```

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


### The Trait
Below is an abbreviated example of the requestCompression trait in use:

```smithy
@requestCompression(
    encodings: ["gzip", "custom"]
)
operation ...
```

The SDK has its own list of supported compression algorithms as a list of `CompressionAlgorithm`s.

```kotlin
private val supportedCompressionAlgorithms: List<CompressionAlgorithm> = listOf(
    Gzip()
)
```

### Behavior
Each algorithm will be attempted in the order they appear in the trait until one that is supported by the runtime is found. If no algorithm is found the request will continue without compression applied.

If there is success when attempting to compress the request then an HTTP header will be either added to the request (containing the algorithm used) or the compression algorithm will be appended to the list of content encodings at the end of an existing header. The header being `Content-Encoding`.

```http request
// There was no encoding in header before compression
POST /upload-data HTTP/1.1
Host: example.com
Content-Type: application/json
Content-Encoding: gzip

// There was an encoding in header before compression
POST /upload-data HTTP/1.1
Host: example.com
Content-Type: application/json
Content-Encoding: br, gzip 
```

### Compression Algorithms
Given that Kotlin is multi-platform there should be multiple "implementations" of each compression algorithm. As of the time of writing this doc (11/07/2023) Gzip is the only supported compression algorithm by the server side and the only one supported for
the SDK. For the time being, only Gzip for JVM is supported specifically. This is done by using the following packages from the Java standard library: `GZIPInputStream` and `GZIPOutputStream`. The other KMP targets are TODO's, and we have plans of using the CRT implementation for those.

We'll be able to add new ones as they start becoming available/required using the compression algorithm interface we've written and
by adding them to the list of supported algorithms. The same goes for non JVM KMP targets, these can be added by filling in the `actual` declarations for the target.

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
        get() = id
    
    /**
     * Compresses a payload
     */
    public suspend fun compress(stream: ByteStream?): ByteStream
}
```

The `id` is used to match a supported `CompressionAlgorithm` to a requested compression algorithm from the trait. The `compress` function is used to compress HTTP bodies.

#### Compression
Gzip JVM non streaming compression implementation:
```kotlin
actual override fun compress(content: ByteArray?): ByteArray {
    val byteArrayStream = ByteArrayOutputStream()
    val gzipStream = GZIPOutputStream(byteArrayStream)

    content?.let { gzipStream.use { it.write(content) } }

    return byteArrayStream.toByteArray()
}
```

Gzip JVM streaming compression implementation:

```kotlin
/**

Plan on writing code to convert SourceContent & ChannelContent to  
`java.io.OutputStream` to use `GZIPOutputStream` in similar fashion as above.
`GZIPOutputStream` takes in a `java.io.OutputStream` which `ByteArrayOutputStream` is.

Could also use function to convert stream to byte array but chances are will run out
of memory doing so. Body will probably be large if using streaming trait on it.

If that code to convert the types is not possible for some reason then will have to 
hand write gzip implementation.
 
**/
```

Allowing user supplied compression algorithms is possible if it's something that wants to be done. There are a couple of issues with this feature that aren't impossible to overcome but certainly pose a challenge that might not be worth it. Some of those include:

1. Server side would be unable to decompress a random compression algorithm provided by a user. There might be no way around this unless a user is implementing a compression algorithm that the SDK would not support at the time. Then there is some niche use case for this.

2. Allowing a user supplied compression algorithm that adjusts the "level" of compression is a good idea but in practice the server side might not be able to handle this functionality and this could break things for the customer.

3. Customers might want to use their own implementations of compressing algorithms but this seems unnecessary.

That level of client configurability comes with some caveats, and a decision was made to not pursue this feature. Regardless of that this can always be added later if it starts to seem necessary/required.

### Implementation
This feature is implemented by registering an [interceptor](interceptors.md) that replaces the outgoing request body with a compressed equivalent.

The interceptor holds most of the logic for this feature to work. The search logic for requested to supported compression algorithms is inside it. The actual compression is initiated here and the header logic as well. Finally, the list of supported compression algorithms is inside it. It could be refactored out, but it lives inside the interceptor for the time being as there are no real use cases for it outside the interceptor.

```kotlin
/**
 * HTTP interceptor that compresses operation request payloads when eligible
 */
public class RequestCompressionTraitInterceptor(
    compressionDisabled: Boolean?,
    compressionThreshold: Int?,
    private val requestedCompressionAlgorithms: List<String>,
) : HttpInterceptor {

    // Set defaults for settings if they are not set
    private val compressionDisabled = compressionDisabled ?: false
    private val compressionThreshold = compressionThreshold ?: 10240

    // List of supported compression algorithms
    private val supportedCompressionAlgorithms: List<CompressionAlgorithm> = listOf(
        Gzip()
    )

    override suspend fun modifyBeforeRetryLoop(
        context: ProtocolRequestInterceptorContext<Any, HttpRequest>
    ): HttpRequest {

        // Verify min compression size setting is in range
        val compressionThresholdAcceptableRange = 0..10485760
        if (!compressionThresholdAcceptableRange.contains(compressionThreshold))
            throw Exception("SDK config property 'requestMinCompressionSizeBytes' must be a value between 0 and 10485760 (inclusive)")

        // Determine if going forward with compression
        val payloadSize = context.protocolRequest.body.contentLength
        val streamingPayload = payloadSize == null
        if (!compressionDisabled && (streamingPayload || payloadSize!! >= compressionThreshold)) {

            // Check if requested algorithm(s) is supported
            findAlgorithm()?.let { algorithm ->

                // Attempt compression
                val request = context.protocolRequest.toBuilder()
                request.body = request.body
                // TODO: Add compression for streams
                addHeader(request, algorithm.algorithmId())
                return request.build()
            }
        }
        return context.protocolRequest
    }
}
```

Supporting function to help find if requested algorithm is supported:
```kotlin
/**
 * Finds first match in supported and request
 */
private fun findAlgorithm() : CompressionAlgorithm? {
    requestedCompressionAlgorithms.forEach { requestedAlgorithmAlgorithmId ->
        supportedCompressionAlgorithms.forEach { supportedAlgorithm ->
            if (supportedAlgorithm.algorithmId() == requestedAlgorithmAlgorithmId) return supportedAlgorithm
        }
    }
    return null
}

// TODO: Return list instead of single algorithm

```

Supporting function to add header or modify header once compression is successful:
```kotlin
/**
 * Appends the algorithm id to the content encoding header. Doesn't remove old content encodings if already present
 * in header
 */
private fun addHeader(request: HttpRequestBuilder, algorithmId: String) {
    val previousEncodings = request.headers["Content-Encoding"]
    val contentEncodingHeaderPrefix = previousEncodings?.let { "$previousEncodings, " } ?: ""

    request.headers.remove("Content-Encoding")
    request.header(
        "Content-Encoding",
        "$contentEncodingHeaderPrefix${algorithmId}"
    )
}
```

### Testing
Testing is done using both unit tests and Smithy HTTP protocol compliance tests. The SDK unit tests are used to verify behaviour and ensure it doesn't drift over time. AWS HTTP protocol tests are used to test the behavior in a more integrated way.

### Appendix

#### Codegen
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

    // Middleware
    private val requestCompressionTraitMiddleware = object : ProtocolMiddleware {
        private val interceptorSymbol = RuntimeTypes.HttpClient.Interceptors.RequestCompressionTraitInterceptor
        override val name: String = "RequestCompressionTrait"

        // Will add interceptor to operation(s) in service with `requestCompression` trait 
        override fun render(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
            if (op.hasTrait<RequestCompressionTrait>()) {
                val requestedCompressionAlgorithms = op.getTrait<RequestCompressionTrait>()!!.encodings

                writer.withBlock(
                    "op.interceptors.add(#T(",
                    "))",
                    interceptorSymbol,
                ) {
                    write("config.disableRequestCompression")
                    write("config.requestMinCompressionSizeBytes")
                    write(requestedCompressionAlgorithms)
                }
            }
        }
    }
}
```

Interceptor registered on an operation:
```kotlin
...
op.interceptors.add(
    RequestCompressionTraitInterceptor(
        config.disableRequestCompression,
        config.requestMinCompressionSizeBytes
        listOf("gzip", "custom"),
    )
)
...
```

## References
[Smithy request compression trait](https://smithy.io/2.0/spec/behavior-traits.html?highlight=requestcompression#requestcompression-trait)

[Smithy IDL Docs](https://smithy.io/2.0/index.html)

[Smithy HTTP protocol compliance test for `requestCompression`](https://github.com/smithy-lang/smithy/blob/806b624733e3fc0e2d3a866ff986d5a63bb96274/smithy-aws-protocol-tests/model/awsJson1_0/requestCompression.smithy)

[Shared  `config` and `credentials` file docs](https://docs.aws.amazon.com/sdkref/latest/guide/file-format.html)

[Docs for Kotlin multi-platform (expect/actual)](https://kotlinlang.org/docs/multiplatform-expect-actual.html)

## Revision history
11/07/2023 - Created