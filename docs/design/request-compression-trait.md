# Request Compression Trait

* **Type**: Design
* **Author(s)**: Omar Perez

## Abstract
Request Compression for the SDK is a feature that allows service clients to compress their requests, notably from client to server only. This feature allows for some degree of configurability and should be easily extendable for future work. As of the time of writing this doc compression should only be supported from service client to server but there are no blockers stopping compression from server to client being implemented in the future.

This document will cover the design decisions behind the implementation of this feature and can serve as future reference for extending, modifying or learning about the feature. It assumes familiarity with the smithy IDL, compression algorithms and things like interceptors and middleware.

## The Trait
To differentiate operations that will be requesting compression the operations will be marked in their smithy model with the `requestCompression` trait.  The trait is applied to operation shapes that have a structure shape as an input (that is either streaming or non-streaming) and the trait should have a list of requested compression algorithms under `encodings`.

```json
@requestCompression(
encodings: ["gzip", "custom"]
)
operation ...
```

The SDK will have its own list of supported compression algorithms as a list of `CompressionAlgorithm`s.

```kotlin
private val supportedCompressionAlgorithms: List<CompressionAlgorithm> = listOf(
    Gzip()
)
```

## Client Configuration

The feature allows for some customizability using settings to change the client configuration. The locations where these settings will be sourced from are as follows (in order of importance) :

1. Explicit client configuration

```kotlin
val client = S3Client {
    region = "us-west-2"
    disableRequestCompression = true
}
```
2. System properties

```kotlin
java -Daws.disableRequestCompression=true
```
3. Environment variables

```kotlin
export AWS_DISABLE_REQUEST_COMPRESSION=true
```
4. AWS profile keys

```kotlin
[default]
disable_request_compression = false
request_min_compression_size_bytes = 200
```

If the client configuration setting is found in location #2 (but not present in location #1) then the rest will be ignored and the setting from location #2 will be used. Same for if the setting is found in location
#1, the rest will be ignored as only one setting will be valid (the setting that is set in the location with
most importance). It helps to think of it like this:

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

#### Disable Request Compression Setting
Service clients will be able to configure if compression is active or not. This is done using a simple boolean setting that will be looked at when the `requestCompression` trait is found. If we see this set to true then we will NOT be compressing the request. It is worth noting that the default value for this configuration option is `false`.

The name for this option will be any of the following based on the locations that it can be set:

1. Explicit client configuration: `disableRequestCompression`

2. System properties: `aws.disableRequestCompression`

3. Environment variables: `AWS_DISABLE_REQUEST_COMPRESSION`

4. AWS profile keys: `disable_request_compression`

#### Minimum Request Compression Size Bytes Setting
Service clients will be able to configure the minimum payload size before compression is active. This is because compression is actually not
beneficial until a certain size in bytes. Anything below that number may typically result in an http body that is larger in size than if it was not compressed. That's why the default value for this configuration setting is 10,240. Otherwise, the setting should be an integer between 0 and 10,485,760. Also, worth noting is that this setting will be ignored if it is determined that the payload is a stream.

The name for this option will be the following based on the location that it is set:

1. Explicit client configuration: `requestMinCompressionSizeBytes`

2. System properties: `aws.requestMinCompressionSizeBytes`

3. Environment variables: `AWS_REQUEST_MIN_COMPRESSION_SIZE_BYTES`

4. AWS profile keys: `request_min_compression_size_bytes`

## Behavior

The SDK will visit each algorithm in the order they appear in the trait until it finds one that's supported. Then it will attempt to compress the request using that algorithm. The application of the compression algorithm may be unsuccessful, in that case one more attempt will be made to apply
the algorithm. If the application is unsuccessful still then it will try to find another algorithm that is both requested and supported. If so, then the process repeats until there are no algorithms left.

**NOTE: I'm not so sure on the paragraph above about retrying the compression. Feel free to vote on removing it or leaving it in. I'm not even sure if it's something that will be needed**

If we reach the end of the list of supported compression algorithms by the SDK and there is no match for any of the requested algorithms then the model is calling for an algorithm that is not yet supported by the SDK. In such case we will not fail with an exception. Instead there will be a log message indicating that the requested compression algorithm(s) is not
supported and the request will be sent uncompressed.

If there is success when attempting to compress the request then an http header will be either added to the request (containing the algorithm used) or the compression algorithm will be appended to the list of content encodings at the end of an existing header. The header being `Content-Encoding`.

```kotlin
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

There is some logic behind the application of the [trait](https://smithy.io/2.0/spec/behavior-traits.html?highlight=requestcompression#requestcompression-trait), but it'll be handled by smithy so there's no need to worry about it. All that the SDK should care about is what type the http body will be once it gets to it. The http body could be any one of these four types:

1. Source Content (streaming)

2. Channel Content (streaming)

3. Bytes (non-streaming)

4. Empty (non-streaming)

And all of those are handled differently. Here's how each one should be handled (in the same order as above):

1. Compression will be applied for streaming HTTP bodies

2. Compression will be applied for streaming HTTP bodies

3. Compression will be applied for non streaming HTTP bodies

4. No compression is applied

Here is how it works in more detail:

```kotlin
val request = context.protocolRequest.toBuilder()

request.body = when(request.body) {
    is HttpBody.SourceContent -> // Apply compression for streaming HTTP bodies
    is HttpBody.ChannelContent -> // Apply compression for streaming HTTP bodies
    is HttpBody.Bytes -> // Apply compression for non streaming HTTP bodies
    is HttpBody.Empty -> // Return as is (no compression is applied)
    else -> throw ClientException("HttpBody type is not supported")
}

request.build()
```

## Compression Algorithms
Given that Kotlin is multi-platform there should be multiple "implementations" of each compression algorithm. As of the time of writing this doc (11/07/2023) Gzip is the only supported compression algorithm by the server side and the only one we will be supporting for
the SDK. For the time being, only Gzip for JVM will be supported specifically. This will be done by using the following packages from the Java standard library: `GZIPInputStream` and `GZIPOutputStream`. The other KMP targets are TODO's, and we have plans of using the CRT implementation for those.

We'll be able to add new ones as they start becoming available/required using the compression algorithm interface we've written and
by adding them to the list of supported algorithms. The same goes for non JVM KMP targets, these can be added by filling in the `actual` declarations for the target.

#### Interfaces
This interface will be used to represent a compression algorithm.

```kotlin
/**
 * Represents a compression algorithm to be used for compressing request payloads on qualifying operations
 */
public interface CompressionAlgorithm {
    /**
     * The ID of the checksum algorithm
     */
    public fun algorithmId(): String

    /**
     * Compresses a non-stream payload
     */
    public fun compress(content: ByteArray?): ByteArray

    /**
     * Compresses a stream payload
     */
    public suspend fun getCompressionStream(stream: ByteStream?): ByteStream
}
```

The `algorithmId()` will be used to match a supported `CompressionAlgorithm` to a requested compression algorithm from the trait. The `compress()` function will be used to compress HTTP bodies that are not streaming. And the `getCompressionStream()` function will do the same but for streaming HTTP bodies.

#### Compression
Since only Gzip for JVM will be supported at the time that is all that is in the design doc. In the future more `actual` implementations may be added here if deemed necessary.

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

---------------------------------------------------

Also looked at how flexible checksums deals with this problem but it's not really transferable :/

**/
```

Allowing user supplied compression algorithms is possible if it's something that wants to be done. There are a couple of issues with this feature that aren't impossible to overcome but certainly pose a challenge that might not be worth it. Some of those include:

1. Server side would be unable to decompress a random compression algorithm provided by a user. There might be no way around this unless a user is implementing a compression algorithm that the SDK would not support at the time. Then there is some niche use case for this.

2. Allowing a user supplied compression algorithm that adjusts the "level" of compression is a good idea but in practice the server side might not be able to handle this functionality and this could break things for the customer.

3. Customers might want to use their own implementations of compressing algorithms but this seems unnecessary.

That level of client configurability comes with some caveats, and a decision was made to not pursue this feature. Regardless of that this can always be added later if it starts to seem necessary/required.

## Implementation
The implementation/integration of the feature is broken down into sections to be able to dive into detail as well as clarity.

### Middleware
We will be using an interceptor to modify the request before checksums are calculated for it. Middleware is how we will be registering the interceptor. This is how this one will work:

* A kotlin integration will look at a service model

* Determine if the service has an operation with the `requestCompression` trait
* If so middleware is registered  for the service
* The middleware will look at each operation in the service individually
* The middleware will then determine if an operation has the `requestCompression` trait
* If so then if will add an interceptor to the operation

More detail on interceptors can be found in this same directory under [interceptors.md](interceptors.md)

Middleware registering interceptor:
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

### Interceptor
The interceptor will hold most of the logic for this feature to work. It'll be setting the default value for the options that are necessary as well as validation for them. The search logic for requested to supported compression algorithms is inside it. The actual compression is initiated here and the header logic as well. Finally the list of supported compression algorithms will be inside it. It could be refactored out but it lives inside the interceptor for the time being as there's no real use cases for it outside of the interceptor

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

## Client Experience
The client experience should be seamless unless they are doing something wrong.

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
Both of these examples would achieve the same result.

## Testing
Testing should be done using both unit tests and Smithy HTTP protocol compliance tests. The  SDK unit tests will be used to verify behaviour and ensure it doesn't drift over time (unless intended to). And each AWS HTTP protocol test will be used to test the behavior in a more integrated way in comparison to the unit tests.

AWS JSON 1.0 smithy protocol test for `requestCompression` trait:
```json
$version: "2.0"

namespace aws.protocoltests.json10

        use aws.protocols#awsJson1_0
        use smithy.test#httpRequestTests
        use smithy.test#httpResponseTests

        apply PutWithContentEncoding @httpRequestTests([
    {
        id: "SDKAppliedContentEncoding_awsJson1_0"
        documentation: "Compression algorithm encoding is appended to the Content-Encoding header."
        protocol: awsJson1_0
        params: {
        "data": """
                NOTE: REMOVED CONTENTS FOR SAKE OF SPACE. TEST CAN BE FOUND IN REFERENCES SECTION 
                """
        }
        method: "POST"
        uri: "/"
        headers: {
        "Content-Encoding": "gzip"
        }
    }
    {
        id: "SDKAppendsGzipAndIgnoresHttpProvidedEncoding_awsJson1_0"
        documentation: """
        Compression algorithm encoding is appended to the Content-Encoding header, and the
        user-provided content-encoding is NOT in the Content-Encoding header since HTTP binding
        traits are ignored in the awsJson1_0 protocol.
        """
        protocol: awsJson1_0
        params: {
        "encoding": "custom"
        "data": """
                NOTE: REMOVED CONTENTS FOR SAKE OF SPACE. TEST CAN BE FOUND IN REFERENCES SECTION 
                """
        }
        method: "POST"
        uri: "/"
        headers: {
        "Content-Encoding": "gzip"
        }
    }
])

@requestCompression(
    encodings: ["gzip"]
)
operation PutWithContentEncoding {
        input: PutWithContentEncodingInput
}

@input
structure PutWithContentEncodingInput {
    @httpHeader("Content-Encoding")
    encoding: String

    data: String
}
```

Unit tests for interceptor:
```kotlin
@Test
fun testCorrectHeaderIsAdded() = runTest {...}

@Test
fun testAlgorithmIsAppendedInHeader() = runTest {...}

@Test
fun testCompressionIsSkipped() = runTest {...}

@Test
fun testGzipCompression() = runTest {...}

@Test
fun testHandlesMultipleCompressionAlgorithms() = runTest {...}

@Test
fun testNoValidCompressionAlgorithm() = runTest {...}
```

These tests should pass with no problem.

## Summary
The request compression trait should be useful for clients that are sending a lot of large requests and could save some bandwidth. What we have right now in this design doc serves as a bare bones implementation of the feature that could eventually be extended to include server to client compression, user supplied compression algorithms, and the like. More compression algorithms could be added as they start being required and it wouldn't be too difficult of a task. It was written with flexibility in mind and should be able to deliver. Some references to things mentioned in passing and assumed to be background knowledge that might be useful are present in the section below.

## References
[Smithy request compression trait](https://smithy.io/2.0/spec/behavior-traits.html?highlight=requestcompression#requestcompression-trait)

[Smithy IDL Docs](https://smithy.io/2.0/index.html)

[Smithy HTTP protocol compliance test for `requestCompression`](https://github.com/smithy-lang/smithy/blob/806b624733e3fc0e2d3a866ff986d5a63bb96274/smithy-aws-protocol-tests/model/awsJson1_0/requestCompression.smithy)

[Shared  `config` and `credentials` file docs](https://docs.aws.amazon.com/sdkref/latest/guide/file-format.html)

[Docs for Kotlin multi-platform (expect/actual)](https://kotlinlang.org/docs/multiplatform-expect-actual.html)

## Revision history
11/07/2023 - Created