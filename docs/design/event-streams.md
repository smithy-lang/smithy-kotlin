# Kotlin Event Streaming Design

* **Type**: Design
* **Author(s)**: Aaron Todd

# Abstract

This document covers the client interfaces to be generated for requests/responses with event stream payloads 
(`@streaming` Smithy trait applied to a `union` shape). See the [event stream spec](https://awslabs.github.io/smithy/1.0/spec/core/stream-traits.html#event-streams).

Reference the additional documents listed in the Appendix for surrounding context on Smithy.


# Design

## Client Interface

### Model

We will use the examples from the event stream spec to explore the client interfaces to be generated:


** Event stream on an input **

```
namespace smithy.example

operation PublishMessages {
    input: PublishMessagesInput
}

@input
structure PublishMessagesInput {
    room: String,
    messages: PublishEvents,
}

@streaming
union PublishEvents {
    message: Message,
    leave: LeaveEvent,
}

structure Message {
    message: String,
}

structure LeaveEvent {}
```

** Event stream on an output **

```
namespace smithy.example

operation SubscribeToMovements {
    input: SubscribeToMovementsInput,
    output: SubscribeToMovementsOutput
}

@input
structure SubscribeToMovementsInput {}

@output
structure SubscribeToMovementsOutput {
    movements: MovementEvents,
}

@streaming
union MovementEvents {
    up: Movement,
    down: Movement,
    left: Movement,
    right: Movement,
    throttlingError: ThrottlingError
}

structure Movement {
    velocity: Float,
}

/// An example error emitted when the client is throttled
/// and should terminate the event stream.
@error("client")
@retryable(throttling: true)
structure ThrottlingError {}
```






### Event Stream Type Representation

The members of an operation input or output that target a stream will be represented using by an asynchronous [Flow](https://kotlinlang.org/docs/reference/coroutines/flow.html) 
from the `kotlinx-coroutines-core` library. This was chosen for pagination and already in use as part of our public API contract.


The following types and service would be generated. 

NOTE: only the input and output types are shown, the other structures or unions in the model would be generated as described in [Kotlin Smithy Design](kotlin-smithy-sdk.md).

#### Input Event Streams


```kt
class PublishMessagesRequest private constructor(builder: Builder){
    val room: String? = builder.room
    val messages: Flow<PublishEvents>? = builder.messages

    ...

    public class Builder {
        var room: String? = null
        var messages: Flow<PublishEvents>? = null
        fun build(): PublishMessagesRequest = PublishMessagesRequest(this)
    }
}

```

#### Output Event Streams

Output event streams would be modeled the same way as input streams. The response object would have a field 

```kt
class SubscribeToMovementsResponse private constructor(builder: Builder){
    val movements: Flow<PublishEvents>? = builder.movements

    ...

    public class Builder {
        var movements: Flow<MovementEvents>? = null
        fun build(): SubscribeToMovementsResponse = SubscribeToMovementsResponse(this)
    }
}

```



### **Service and Usage**

NOTE: There are types and internal details here not important to the design of how customers will interact with 
streaming requests/responses (e.g. serialization/deserialization). 
Those details are subject to change and not part of this design document. The focus here should be on the way 
streaming is exposed to a customer.


```kt
package aws.sdk.kotlin.service.Example

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

suspend fun getObject(block: GetObjectRequest.Builder.() -> Unit): GetObjectResponse {
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


## Java Interop

TODO - fill in how Flow and rx/new reactive Java can be used together via coroutine wrapper libs provided by JB


## Additional References

* [Smithy Core Spec](https://awslabs.github.io/smithy/1.0/spec/core/shapes.html)
* [Event Stream Spec](https://awslabs.github.io/smithy/1.0/spec/core/stream-traits.html#event-streams).
* [Kotlin Asynchronous Flow](https://kotlinlang.org/docs/reference/coroutines/flow.html) 
* [Kotlin Smithy SDK](kotlin-smithy-sdk.md)
* [Binary Streaming](binary-streaming.md)

# Revision history

* 01/19/2022 - Created
