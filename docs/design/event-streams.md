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


**Event stream on an input**

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

**Event stream on an output**

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

The members of an operation input or output that target a stream will be represented with an asynchronous [Flow](https://kotlinlang.org/docs/reference/coroutines/flow.html) 
from the `kotlinx-coroutines-core` library. `Flow` is a natural fit for representing asynchronous streams. 

`Flow` was chosen for pagination and already in use as part of our public API contract. Any alternative to this would require a custom but similar type that doesn't play well with
the rest of the coroutine ecosystem. There is also prior art for representing streaming requests and responses, see [gRPC Kotlin](https://github.com/grpc/grpc-kotlin).

The following types and service would be generated. 

NOTE: only the input and output types are shown, the other structures or unions in the model would be generated as described in [Kotlin Smithy Design](kotlin-smithy-sdk.md) with the
exception of event stream members targeting errors which is described below in more detail.

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

Output event streams would be modeled the same way as input streams. The response object would have a `Flow<T>` field that represents the response stream.

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


Modeling the event stream as a field of the request or response allows for [initial messages](https://awslabs.github.io/smithy/1.0/spec/core/stream-traits.html#initial-messages) 
to be implemented. If we directly returned or took a `Flow<T>` as the input or output type we would not be able to represent the initial request or response fields when present.


#### Event Stream Error Representation

Event stream unions may model exceptions that can appear on the stream. These exceptions are terminal messages that are intended to be surfaced to the client using
idiomatic error handling mechansims of the target language. This means that in the model the possible (modeled) errors a consumer may see after the stream has started
are part of the overall union that makes up the possible events. 

NOTE: the set of errors on the operation MAY not be the same set of errors modeled on the event stream.


Using the example from above: 

```
@streaming
union MovementEvents {
    up: Movement,
    down: Movement,
    left: Movement,
    right: Movement,
    throttlingError: ThrottlingError
}

```

The default representation of a union (as documented in [Kotlin Smithy Design](kotlin-smithy-sdk.md)) is generated as:

```kotlin
sealed class MovementEvents {
    data class Up(val value: Movement): MovementEvents()
    data class Down(val value: Movement): MovementEvents()
    data class Left(val value: Movement): MovementEvents()
    data class Right(val value: Movement): MovementEvents()
    data class ThrottlingError(val value: ThrottlingError): MovementEvents()
    object SdkUnknown : MovementEvents()
}
```

This is undesirable though since event stream errors are terminal and end the stream. Keeping them in the set of possible events also means it may be easier for consumers to ignore
errors depending on what events they are looking for (e.g. by having a catch all `else` branch they may inadvertently ignore an error and think the stream completed successfully).


Event stream unions will be special cased to filter out variants targeting error shapes. When these errors are emitted by the service on the stream they will be converted
to the appropriate modeled exception and thrown rather than being emitted on the stream the consumer sees.

As an example, the generated event stream union will look like this (note the absence of `ThrottlingError`):

```kotlin
sealed class MovementEvents {
    data class Up(val value: Movement): MovementEvents()
    data class Down(val value: Movement): MovementEvents()
    data class Left(val value: Movement): MovementEvents()
    data class Right(val value: Movement): MovementEvents()
    object SdkUnknown : MovementEvents()
}
```

### **Service and Usage**

NOTE: There are types and internal details here not important to the design of how customers will interact with 
streaming requests/responses (e.g. serialization/deserialization). 
Those details are subject to change and not part of this design document. The focus here is on the way streaming is exposed to a customer.


The signatures generated match that of binary streaming requests and responses. Notably that output streams take a lambda instead of returning 
the response directly (see [binary-streaming design](binary-streaming.md) which discusses this pattern).
The response (and event stream) are only valid in that scope, after which the resources consumed by the stream are closed and no longer valid.


```kt
package aws.sdk.kotlin.service.Example

interface ExampleClient: SdkClient {

    // input event stream signature
    suspend fun publishMessages(input: PublishMessagesRequest): PublishMessagesResponse 

    // output event stream signature
    suspend fun <T> subscribeToMovements(input: SubscribeToMovementsRequest, block: suspend (SubscribeToMovementsResponse) -> T): T 
}
```


Example Usage

```kt

// NOTE: Flows are cold, they do nothing until collected. They BODY will not be ran until then.
suspend fun generateMessages(): Flow<PublishEvents> = flow {
    // BODY
    repeat(5) {
        emit(PublishEvents.Message("message-$it"))
    }

    emit(PublishEvents.LeaveEvent())
}


fun main() = runBlocking{
    val client = ExampleClient()

    // STREAMING REQUEST BODY EXAMPLE
    val publishRequest = PublishMessagesRequest {
        room = "test-room"
        messages = generateMessages()

    }

    client.publishMessages(publishRequest)


    // STREAMING RESPONSE BODY EXAMPLE

    val subscribeRequest = SubscribeToMovementsRequest { }

    client.subscribeToMovements(subscribeRequest) { resp ->
        resp.movements.collect { event ->
            when(event) {
                is MovementEvents.Up,
                is MovementEvents.Down,
                is MovementEvents.Left,
                is MovementEvents.Right -> handleMovement(event)
                else -> error("unknown event type: $event")
            }
        }

    }  // the response/stream will no longer be valid at the end of this block though

}

private fun handleMovement(event: MovementEvents) { ... }
```

Accepting a lambda matches what is generated for binary streams (see [binary-streaming design](binary-streaming.md)) and will provide a consistent API experience as well
as the same benefits to the SDK (properly scoped lifetime for resources). 


# Appendix


## Java Interop

`Flow<T>` is not easily consumable directly from Java due to the `suspend` nature of it. JetBrains provides 
[reactive adapters](https://github.com/Kotlin/kotlinx.coroutines/tree/master/reactive) that can be used to convert rxJava and JDK-9 
reactive streams to or from an equivalent `Flow`. Users would be responsible for creating a shim layer using these primitives provided
by JetBrains which would allow them to expose the Kotlin functions however they see fit to their applications. 


## Additional References

* [Smithy Core Spec](https://awslabs.github.io/smithy/1.0/spec/core/shapes.html)
* [Event Stream Spec](https://awslabs.github.io/smithy/1.0/spec/core/stream-traits.html#event-streams)
* [Kotlin Asynchronous Flow](https://kotlinlang.org/docs/reference/coroutines/flow.html) 
* [Kotlin Smithy SDK](kotlin-smithy-sdk.md)
* [Binary Streaming](binary-streaming.md)

# Revision history

* 01/19/2022 - Created
