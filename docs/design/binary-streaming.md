# Kotlin (Binary) Streaming Request/Response Bodies Design

* **Type**: Design
* **Author(s)**: Aaron Todd

# Abstract

This document covers the client interfaces to be generated for requests/responses with streaming binary payloads 
(`@streaming` Smithy trait applied to a `blob` shape). 

The design of generic streams (streams that target a `union` shape) will be covered separately by the 
[event stream spec](https://awslabs.github.io/smithy/1.0/spec/core/stream-traits.html#event-streams) *in a future design document.*

Reference the additional documents listed in the Appendix for surrounding context on Smithy.


# Design

## Client Interface

### Model

Given the following (abbreviated and simplified) Smithy model representing S3's PutObject and GetObject requests 
(which represent streaming binary request and response payloads respectively):


```
service S3 {
    version: "2015-03-31",
    operations: [PutObject, GetObject]
}


@http(method: "POST", uri: "/{Bucket}/{Key}")
operation PutObject{
    input: PutObjectRequest,
    output: PutObjectResponse,
}

@streaming
blob S3Body

struct PutObjectRequest {
    @httpLabel
    @required
    bucket: String

    @httpLabel
    @required
    key: String
    
    @required
    @httpPayload
    body: S3Body
}
struct PutObjectResponse {
    @httpHeader("X-Amz-Version-Id")
    versionId: String
    ...
}

@http(method: "GET", uri: "/{Bucket}/{Key}")
operation GetObject{
    input: GetObjectRequest,
    output: GetObjectResponse,
}

struct GetObjectRequest {
    @httpLabel
    @required
    bucket: String

    @httpLabel
    @required
    key: String

    ...
}

struct GetObjectResponse {
    @httpHeader("X-Amz-Version-Id")
    versionId: String

    @required
    @httpPayload
    body: S3Body
}

```


The following types and service would be generated (note the Java `Builder` implementation has been left off for brevity).

### Put Object
See the Appendix for an overview of the `ByteStream` type.

```kt

import software.aws.clientrt.content.ByteStream

class PutObjectRequest private constructor(builder: BuilderImpl){
    val bucket: String? = builder.bucket
    val key: String? = builder.key
    val body: ByteStream? = builder.body

    companion object {
        operator fun invoke(block: DslBuilder.() -> Unit) = BuilderImpl().apply(block).build()
    }

    interface Builder {
        fun build(): PutObjectRequest
    }

    interface DslBuilder {
        var body: ByteStream?
        var bucket: String?
        var key: String?
    }

    private class BuilderImpl : Builder, DslBuilder {
        override var body: ByteStream? = null
        override var bucket: String? = null
        override var key: String? = null
        override fun build(): PutObjectRequest = PutObjectRequest(this)
    }
}


class PutObjectResponse private constructor(builder: BuilderImpl){
    val versionId: String? = builder.versionId

    companion object {
        operator fun invoke(block: DslBuilder.() -> Unit) = BuilderImpl().apply(block).build()
    }

    interface Builder {
        fun build(): PutObjectResponse
    }

    interface DslBuilder {
        var versionId: String?
    }

    private class BuilderImpl : Builder, DslBuilder {
        override var versionId: String? = null
        override fun build(): PutObjectResponse = PutObjectResponse(this)
    }
}

```



### Get Object

```kt
package com.amazonaws.service.s3.model

class GetObjectRequest private constructor(builder: BuilderImpl){
    val bucket: String? = builder.bucket
    val key: String? = builder.key

    companion object {
        operator fun invoke(block: DslBuilder.() -> Unit) = BuilderImpl().apply(block).build()
    }

    interface Builder {
        fun build(): GetObjectRequest
    }

    interface DslBuilder {
        var bucket: String?
        var key: String?
    }

    private class BuilderImpl : Builder, DslBuilder {
        override var bucket: String? = null
        override var key: String? = null
        override fun build(): GetObjectRequest = GetObjectRequest(this)
    }
}


import software.aws.clientrt.content.ByteStream

class GetObjectResponse private constructor(builder: BuilderImpl){

    val body: ByteStream? = builder.body
    val versionId: String? = builder.versionId

    companion object {
        operator fun invoke(block: DslBuilder.() -> Unit) = BuilderImpl().apply(block).build()
    }

    interface Builder {
        fun build(): GetObjectResponse
    }

    interface DslBuilder {
        var body: ByteStream?
        var versionId: String?
    }

    private class BuilderImpl : Builder, DslBuilder {
        override var body: ByteStream? = null
        override var versionId: String? = null
        override fun build(): GetObjectResponse = GetObjectResponse(this)
    }
}


```



### **Service and Usage**

NOTE: There are types and internal details here not important to the design of how customers will interact with 
streaming requests/responses (e.g. serialization/deserialization). 
Those details are subject to change and not part of this design document. The focus here should be on the way 
streaming is exposed to a customer.


```kt
package com.amazonaws.service.s3

class S3Client: SdkClient {
    private val client: SdkHttpClient

    // initialization/configuration details omitted
    suspend fun putObject(input: PutObjectRequest): PutObjectResponse {
        return client.roundTrip(PutObjectRequestSerializer(input), PutObjectResponseDeserializer())
    }

    // Streaming response body Alternative 1
    suspend fun <T> getObjectAlt1(input: GetObjectRequest, block: suspend (GetObjectResponse) -> T): T {
        val response: GetObjectResponse = client.roundTrip(GetObjectRequestSerializer(input), GetObjectResponseDeserializer())
        try {
            return block(response)
        } finally {
            // perform cleanup / release network resources
            response.body?.cancel()
        }
    }

    // Streaming response body Alternative 2
    suspend fun getObjectAlt2(input: GetObjectRequest): GetObjectResponse {
        return client.roundTrip(GetObjectRequestSerializer(input), GetObjectResponseDeserializer())
    }
}
```


Example Usage

```kt
fun main() = runBlocking{
    val service = S3Client()

    // STREAMING REQUEST BODY EXAMPLE
    val putRequest = PutObjectRequest{
        body = ByteStream.fromString("my bucket content") 
        bucket = "my-bucket"
        key = "config.txt"
    }

    val putObjResp = service.putObject(putRequest)
    println(putObjResp)

    val getRequest = GetObjectRequest {
        bucket = "my-bucket"
        key = "lorem-ipsum"
    }

    // STREAMING RESPONSE BODY EXAMPLE(S)

    println("GetObjectRequest::Alternative 1")
    service.getObjectAlt1(getRequest) { resp ->
        // do whatever you need to do with resp / body
        val bytes = resp.body?.toByteArray()
        println("content length: ${bytes?.size}")
        // optionally return any type you want from here
        // return@getObjectAlt1 bytes
    }  // the response will no longer be valid at the end of this block though


    println("GetObjectRequest::Alternative 2")
    val getObjResp = service.getObjectAlt2(getRequest)
    println(getObjResp.body?.decodeToString())
}
```



For completeness the following is an example of reading the stream manually:

```kt
    // example of reading the response body as a stream (without going through one of the
    // provided transforms e.g. decodeToString(), toByteArray(), toFile(), etc)
    val getObjResp2 = service.getObjectAlt2(getRequest)
    getObjResp2.body?.let { body ->
        val stream = body as ByteStream.Reader
        val source = stream.readFrom()
        // read (up to) 64 bytes at a time
        val buffer = ByteArray(64)
        var bytesRead = 0

        while(!source.isClosedForRead) {
            val read = source.readAvailable(buffer, 0, buffer.size)
            val contents = buffer.decodeToString()
            println("read: $contents")
            if (read > 0) bytesRead += read
        }
        println("read total of $bytesRead bytes")
    }
```

The analogous interface for writing to a stream may or may not be provided out of the box. There are some 
considerations there whether we want to support that or wait for something like [kotlinx-io](https://github.com/Kotlin/kotlinx-io)
to be standardized and then provide wrappers for plugging those types in. We would definitely provide abstractions 
for transforming files, ByteArray, and Strings at a minimum though. There are many IO libraries though 
(OkIO, Ktor, kotlinx-io, etc) and it may just be enough to provide examples of how to adapt them to 
`ByteStream/SdkByteReadChannel` rather than trying to roll our own or favor one over the other.

### Response Alternatives

There are two alternatives presented for dealing with streaming responses. 

The first alternative has the advantage of a clear lifetime of when the response stream will be closed. The 
problem with this approach is that it conflicts with the DSL style overloads that have been discussed and are 
commonly found in Kotlin APIs.


e.g.

```kt
suspend fun getObject(input: GetObjectRequest): GetObjectResponse { ... }

suspend fun getObject(block: GetObjectRequest.DslBuilder.() -> Unit): GetObjectResponse {
    val input = GetObjectRequest.invoke(block)    
    return getObject(input)
}
```


These DSL builder overloads allow callers to construct the request as part of the call:

```kt
val resp = service.getObject {
    bucket = "my-bucket"
    key = "my-key"
}

```


Alternative 1 conflicts with this overload and breaks the principle of least surprise if all other non-streaming 
requests supply such an overload. 


Alternative 2 presents a different problem of knowing when the response stream has been consumed by the caller and 
resources can be released and cleaned up. Of course the most likely use cases (e.g. writing to a file, conversion
to in-memory buffer) would be provided by the SDK and close the stream for the user (as shown in the example). 
That just leaves if the user decides to not consume the body immediately or manually control reading the body 
there is a chance underlying resources could be leaked. The underlying stream type would implement `Closeable` 
and forgetting to close the resource would represent a misuse of the API much like any other resource that 
isn't closed properly. Kotlin provides methods for ensuring types marked closeable are closed via [use](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.io/use.html).

e.g.


```kt
resp.body?.use {
    // do whatever you are going to do with the stream
} // closed at the end of this block
```


The advantage of this approach is flexibility in how the response is consumed and API methods all having a similar look and feel.

**Recommendation 6/23/2020**

After discussion and feedback from design review the recommendation will be to pursue Alternative 1 with minor 
updates to return the result of the block invoked (already captured in the code examples above). Alternative 1
leaves the door open to add other overloads such as alternative 2 or the DSL overload at a later date if there 
is demand for it while presenting the most conservative option for the runtime (resources can be cleaned up at a 
known point in time). 



# Appendix

## `ByteStream`

The definition of the `ByteStream` type shown in the examples is given below. Whenever a Smithy model targets 
streaming blob shape this would be the symbol those shapes are mapped to in codegen.


```kt
/**
 * Represents an abstract stream of bytes
 */
sealed class ByteStream {

    /**
     * The content length if known
     */
    open val contentLength: Long? = null

    /**
     * Variant of a [ByteStream] with payload represented as an in-memory byte buffer.
     */
    abstract class Buffer : ByteStream() {
        /**
         * Provides [ByteArray] to be consumed
         */
        abstract fun bytes(): ByteArray
    }

    /**
     * Variant of an [ByteStream] with a streaming payload. Content is read from the given source
     */
    abstract class Reader : ByteStream() {
        /**
         * Provides [SdkByteReadChannel] to read from/consume
         */
        abstract fun readFrom(): SdkByteReadChannel
    }

    companion object {
        /**
         * Create a [ByteStream] from a [String]
         */
        fun fromString(str: String): ByteStream = StringContent(str)

        /**
         * Create a [ByteStream] from a [ByteArray]
         */
        fun fromBytes(bytes: ByteArray): ByteStream = ByteArrayContent(bytes)

        /**
         * Create a [ByteStream] from a [File]
         */
        fun fromFile(file: File): ByteStream = LocalFileContent(file)
    }
}


/**
 * Consume a [ByteStream] as a [ByteArray]
 */
suspend fun ByteStream.toByteArray(): ByteArray {
    val stream = this
    return when(stream) {
        is ByteStream.Buffer -> stream.bytes()
        is ByteStream.Reader -> stream.readFrom().readAll()
    }
}

/**
 * Consume a [ByteStream] and decode the contests as a string
 */
@OptIn(ExperimentalStdlibApi::class)
suspend fun ByteStream.decodeToString(): String = toByteArray().decodeToString()


/**
 * Consume a [ByteStream] and write the contents to a file
 */
// TODO
// suspend fun ByteStream.toFile(..) {...}

```



The `SdkByteReadChannel` interface is given below and represents an abstract channel to read bytes from, it's 
definition is subject to change:


```kt
/**
 * Supplies a stream of bytes. Use this interface to read data from wherever it’s located: from the network, storage, or a buffer in memory.
 *
 * This interface is functionally equivalent to an asynchronous coroutine compatible [java.io.InputStream]
 */
interface SdkByteReadChannel {
    /**
     * Returns number of bytes that can be read without suspension. Read operations do no suspend and return immediately when this number is at least the number of bytes requested for read.
     */
    val availableForRead: Int

    /**
     * Returns true if the channel is closed and no remaining bytes are available for read. It implies that availableForRead is zero.
     */
    val isClosedForRead: Boolean

    /**
     * Read the entire content into a [ByteArray]. NOTE: Be careful this will read the entire byte stream into memory.
     */
    suspend fun readAll(): ByteArray

    /**
     * Reads all available bytes to [sink] buffer and returns immediately or suspends if no bytes available
     */
    suspend fun readAvailable(sink: ByteArray, offset: Int, length: Int): Int

    /**
     * Close channel with optional cause cancellation
     */
    fun cancel(cause: Throwable?): Boolean
}

```

### Why special case binary streams (as e.g. `ByteStream`)?

All data is just binary data on disk or in memory, but some usages are so common that they make sense to provide an 
out of the box experience for customers. 

Empirically we know that the most common use cases revolve around representing binary data as either a file saved 
to hard disk or in-memory as either a buffer of raw bytes or as string data. There will be other less common use 
cases that may be of interest (e.g. reading a file from a URI off the network) but at a minimum we should provide 
transforms for dealing with these three use cases. The `ByteStream` type allows this flexibility.

The `ByteStream` type has a lot of nice properties:

1. It can be used for both requests and responses
2. It is coroutine compatible
    1. This is a *big* deal. We need to strive to be coroutine compatible throughout our API. This puts the least 
    number of assumptions on how a customer can use the SDK. If we opt for blocking calls somewhere it’s going to break their expectations.
3. It doesn’t violate open-close principle
    1. Customers are able to plug in their own compatible types if the provided ones don’t fit their use case
    2. We can provide new types if the use case is common enough
4. The out of the box experience for common use cases is easy to use and feels like setting any other class property on the request side or consuming them on the response side
    1. This one is particularly important from a usability standpoint. Any alternative needs to provide at least as good or better ease of use story for producing or consuming common types (e.g. File, ByteArray, String) as well as flexible enough to handle advanced user provided use cases.
5. Due to (1) it makes codegen easier (any time you see `@streaming blob` shapes you can substitute `ByteStream` as the property type)
    1. If I had to prioritize properties this would be low on the list but it is nevertheless a “nice property”
6. Being a sealed class with interfaces as the variants it can easily be implemented for Kotlin MPP
7. The `SdkByteReadChannel` interface on which the streaming variant depends on allows for back pressure to occur
8. It can handle streams of both known and unknown size (e.g. File, ByteArray, and String are all known, reading from a socket is unknown)

### Viable Alternatives to `ByteStream`

One alternative to a custom type such as `ByteStream` would be to utilize [Kotlin’s Flow type](https://kotlinlang.org/docs/reference/coroutines/flow.html#asynchronous-flow). Instead of `ByteStream` we would represent a binary stream as `Flow<ByteArray>`. 


> Suspending functions asynchronously returns a single value, but how can we return multiple asynchronously computed values? This is where Kotlin Flows come in.


A simplified example of the request/response would look like:


```kt
class PutObjectRequest private constructor(builder: BuilderImpl) {
    val body: Flow<ByteArray>? = builder.body
}


class GetObjectResponse private constructor(builder: BuilderImpl) {
    val body: Flow<ByteArray>? = builder.body
}

```

Usage of this type would be something like the following then:


```kt
// PutObject
val putObjectRequest = PutObjectRequest {
    body = fromByteArray(byteArrayOf(1,2,3))
    // alternatively you don't need the transform here, you could just do
    // flowOf(byteArrayOf(1,2,3)) OR flowOf("my string".toByteArray())
}
service.putObject(putObjectRequest)

// GetObject (ALT-2 shown)
val getObjectRequest = GetObjectRequest {...}
val resp = service.getObject(getObjectRequest)

println("decoding obj resp")
val contents = resp.body?.decodeToString()
println("contents: $contents")

// Example of consuming the stream directly
resp.body?.collect { value ->
    val contents = value.decodeToString()
    println("recv'd: $contents")
}
```


The same set of transforms for the common use cases would be provided by the client runtime:

```kt

fun fromByteArray(array: ByteArray): Flow<ByteArray> = flowOf(array)
fun fromString(string: String): Flow<ByteArray> = fromByteArray(string.toByteArray())
fun fromFile(file: File): Flow<ByteArray> = TODO()


@OptIn(ExperimentalStdlibApi::class)
suspend fun Flow<ByteArray>.decodeToString(): String = toByteArray().decodeToString()

// NOTE: this is just an example, there are potentially more efficient ways to do this
suspend fun Flow<ByteArray>.toByteArray(): ByteArray = this.reduce { accumulator, value ->  accumulator + value }

suspend fun Flow<ByteArray>.toFile(): String = TODO()

```


This approach hasn’t been fully fleshed out but on the surface it should have roughly the same set of properties as the 
custom `ByteStream` type. In addition it may feel more familiar to customers (although we still expect customers to 
go through a provided transform most of the time so the differences are most acutely felt in directly producing or 
consuming a raw stream). A drawback of this approach is the client runtime will have to deal with some of the 
complexities of consuming or producing the result as a flow which may include spinning up coroutines to do so. 
Finally, `Flow` is relatively new still and only recently stabilized.

## Java Interop

**Why not InputStream/OutputStream?**
[InputStream](https://docs.oracle.com/javase/7/docs/api/java/io/InputStream.html) and [OutputStream](https://docs.oracle.com/javase/7/docs/api/java/io/OutputStream.html) are builtin Java types 

The issue with going that route is two fold:

1.  They aren’t coroutine friendly; you *can* use them but they are blocking calls
2. They aren’t Kotlin Multi Platform compatible (JVM only) which doesn’t matter as much to Android but if the SDK team is going to pursue an MPP SDK then it will matter to them potentially
    

**Accessing Streams From Java**

* The transform methods (e.g. `fromFile()`, `fromString()`, `fromByteArray()`, etc) are all directly consumable from Java
* Dynamic streaming (i.e. not using a provided transform) will require a shim in Kotlin for either request or response types
* The receiving end (e.g. `GetObjectResponse.body`) will require a shim layer since the transforms are all suspend functions (`suspend fun ByteStream.toFile(...)` or `suspend fun ByteStream.toString(): String`)
    * This may be as simple as: 
        `fun byteStreamToFile(stream: ByteStream, file: File) = runBlocking{ 
            stream.toFile(file) 
        } `

## Additional References

* [Kotlin Smithy SDK](kotlin-smithy-sdk.md)
* [Smithy Core Spec](https://awslabs.github.io/smithy/1.0/spec/core/shapes.html)
* [Kotlin Asynchronous Flow](https://kotlinlang.org/docs/reference/coroutines/flow.html) 

# Revision history

* 6/03/2021 - Initial upload
* 6/11/2020 - Created
