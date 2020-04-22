# Event Streams

Reference to the [Event Stream spec](https://awslabs.github.io/smithy/spec/event-streams.html) which builds on top of the Core spec

## What is an Event Stream?

An event stream is an abstraction that allows multiple messages to be sent asynchronously between a client and server.  Serialization and format of message sent are defined by the protocol in the smithy model.

The operation input or output can be marked with an **`eventStream`** trait. A member that targets a structure is a single-event event stream, and a member that targets a union is a multi-event event stream.

## Single Event Stream Behavior

Clients that send or receive single-event event streams are expected to provide an abstraction to end-users that allows values to be produced or consumed asynchronously for the targeted event structure.

Given this structure where `@eventStream` is on the input of the structure:

```
namespace smithy.example

operation PublishMessages {
    input: PublishMessagesInput
}

structure PublishMessagesInput {
    room: String,

    @eventStream
    messages: Message,
}

structure Message {
    message: String,
}
```

In Kotlin we can use channels to represent this:

```kotlin
// library code
public fun <E> CoroutineScope.consume(
    capacity: Int = 0,
    context: CoroutineContext = EmptyCoroutineContext,
    onCompletion: CompletionHandler? = null,
    @BuilderInference block: suspend ConsumerScope<E>.() -> Unit
): SendChannel<E> {
    val channel = Channel<E>(capacity)
    val newContext = newCoroutineContext(context)
    val coroutine = ConsumerCoroutine(newContext, channel)
    if (onCompletion != null) coroutine.invokeOnCompletion(handler = onCompletion)
    coroutine.start(CoroutineStart.DEFAULT, coroutine, block)
    return coroutine
}

public interface ConsumerScope<in E> : CoroutineScope, ReceiveChannel<E> {

    val channel: Channel<E>
}

internal open class ConsumerCoroutine<E>(
    parentContext: CoroutineContext, 
    channel: Channel<E>,
    active: Boolean
) : ChannelCoroutine<E>(parentContext, channel, active), ConsumerScope<E> {
    override fun onCancelling(cause: Throwable?) {
        _channel.cancel(cause?.let {
            it as? CancellationException ?: CancellationException("$classSimpleName was cancelled", it)
        })
    }
}

//generated code for client
class PublishMessagesInput constructor(capacity: Int = Channel.RENDEZVOUS) {
    var room: String? = null
    var messages: List<Message> = emptyList()
    val capacity = capacity
}

class Message {
    var message: String? = null
}


class FooService {

    suspend fun publishMessages(input: PublishMessagesInput): SendChannel<PublishMessagesInput>> {
        for (msg in input.messages){
        consumer.send(msg)
        }
        consumer.close()
    }

}

// mock server side
private val consumer = consume<Message>(input.capacity) {
    consumeEach {
        println("recv'd: ${it.message}...sending to server")
    }
    println("event stream closed")
}


// example client code
fun main() {
    val service = FooService()
    val input = PublishMessagesInput().apply {room = "foo room", capacity = 5}
    runBlocking {
        launch {
            repeat(5) {
                input.messages.add(Message().apply{message = "message: $it"})
            }

            service.publishMessages(input)
        }
    }
}

```

**Note:** Produce in the below example is an experimental api. Behaviour of producers that work as children in a parent scope with respect to cancellation and error handling may change in the future.

If the `@eventStream` trait were on the output of the smithy model like this:

```
namespace smithy.example

operation SubscribeToMovements {
    output: SubscribeToMovementsOutput
}

structure SubscribeToMovementsOutput {
    @eventStream
    movements: Movement,
}

structure Movement {
    angle: Float,
    velocity: Float,
}
```

our code would differ in that it would use a produce to send so that it could receive from that channel asynchronously. Produce is a coroutine builder that simplifies the process of creating a coroutine and a channel. It will create a new Receieve Channel and launch a coroutine within a ProducerScope to send values on the channel. When there’s nothing left to send, the channel is implicitly closed and the coroutine resource is released.

```kotlin
class Movement {
    var angle: Float
    var velocity: Float
}

class SubscribeToMovementsOutput {
    var movements: Movement
}


class FooService {

    fun CoroutineScope.subscribeToMovements(): ReceiveChannel<SubscribeToMovementsOutput>> = produce {
        // real implementation would setup the network connection and wire it to the send channel
        // here we fake it with a function that produces movements as if it were the server
        launch { produceMovements(it) }
    }

}

// mock server side
private fun produceMovements(ch: SendChannel<Movement>) {
    repeat(5) {
        ch.send(Movement())
    }
}


//to use this generated code in code as a developer you might do something like below:

fun main() {
    val service = FooService()
 
    runBlocking {
        val movements = service.subscribeToMovements()

        movements.consumeEach {println(it)}
    }
}

```

## Multi Event Stream Behavior

A multi-event event stream is an event stream that streams any number of named event structure shapes defined by a union. It is formed when the eventStream trait is applied to a member that targets a union. Each member of the targeted union MUST target a structure shape. The member names of the union define the name that is used to identify each event that is sent over the event stream.

Given this structure where `@eventStream` is on the input of a structure:

```
namespace smithy.example

operation PublishMessages {
    input: PublishMessagesInput
}

structure PublishMessagesInput {
    room: String,

    @eventStream
    messages: PublishEvents,
}

union PublishEvents {
    message: Message,
    leave: LeaveEvent,
}

structure Message {
    message: String,
}

structure LeaveEvent {}
```

Our code might look something like this:

```kotlin
class PublishMessagesInput constructor(capacity: Int = Channel.RENDEZVOUS) {
    val room: String
    val messages: List<PublishEvents>
    val capacity = capacity
}

sealed class PublishEvents

sealed class PublishEvents {
    class Message(val message: String) : Message()
    class LeaveEvent() : Message()
}

class Message{
    val message: String
}

class LeaveEvent: PublishEvents {}


class FooService {

    suspend fun publishMessages(input: PublishMessagesInput): SendChannel<PublishMessagesInput>> {
        for (msg in input.messages){
        consumer.send(msg)
        }
        consumer.close()
    }

}

// mock server side
private val consumer = consume<Message>(input.capacity) {
    consumeEach {
        when (it)
            is PubishEvents.Message -> println("recv'd: ${it}...sending type Message to server")
            is PublishEvents.LeaveEvent -> println("do whatever leave event does or send leave event to server")
        
    }
    println("event stream closed")
}

// example client code
fun main() {
    val service = FooService()
    val input = PublishMessagesInput().apply {room = "foo room", capacity = 5}
    runBlocking {
        launch {
            repeat(2) {
                input.messages.add(Message().apply{message = "message: $it"})
            }

            repeat(2) {
                input.messages.add(LeaveEvent())
            }
        }
        service.publishMessages(input)

        }
    }
}

```

Given this structure where `@eventStream` is on the output of a structure:

```
namespace smithy.example

operation SubscribeToMovements {
    output: SubscribeToMovementsOutput
}

structure SubscribeToMovementsOutput {
    @eventStream
    movements: MovementEvents,
}

union MovementEvents {
    up: Movement,
    down: Movement,
    left: Movement,
    right: Movement,
}

structure Movement {
    velocity: Float,
}
```

Our code might look like the following:


```kotlin

sealed class MovementEvents {
    class Up() : Movement()
    class Down(): Movement()
    class Left(): Movement()
    class Right(): Movement()
}
class Movement {
    var velocity: Float
}

class SubscribeToMovementsOutput {
    var movements: List<MovementEvents>
}


class FooService {

    fun CoroutineScope.subscribeToMovements(): ReceiveChannel<SubscribeToMovementsOutput>> = produce {
        // real implementation would setup the network connection and wire it to the send channel
        // here we fake it with a function that produces movements as if it were the server
        launch { produceMovements(it) }
    }

}

// mock server side
private fun produceMovements(ch: SendChannel<Movement>) {
    repeat(5) {
        ch.send(Movement())
    }
}


//to use this generated code in code as a developer you might do something like below:

fun main() {
    val service = FooService()
 
    runBlocking {
        val movements = service.subscribeToMovements()

        movements.consumeEach {
            when(it)
                is Movement.Up -> println("movement was up")
                is Movement.Down -> println("movement was down")
                is Movement.Left -> println("movement was left")
                is Movement.Right -> println("movement was right")
        }
    }
}

```

There are some additional traits to account for with event streams.

### Initial Messages
An initial message is comprised of the top-level input or output members of an operation that are not targeted by the eventStream trait. Initial messages provide an opportunity for a client or server to provide metadata about an event stream before transmitting events.

If we take our intial single stream input example this might look something like this:
```kotlin

//generated code for client
class PublishMessagesInput constructor(capacity: Int = Channel.RENDEZVOUS) {
    var room: String? = null
    var messages: List<Message> = emptyList()
    val capacity = capacity
}

class Message {
    var message: String? = null
}


class FooService {

    suspend fun publishMessages(input: PublishMessagesInput): SendChannel<PublishMessagesInput>> {

        for (msg in input.messages){
        consumer.send(msg)
        }
        consumer.close()
    }

    suspend fun sendInitialMessage(input: PublishMessagesInput) -> Metadata {
        //sends intial request to the server with room property
        // in this case the property is room that doesnt have the event trait so send `input.room` to server and return metadata.
    }

}

// mock server side
private val consumer = consume<Message>(input.capacity) {
    consumeEach {
        println("recv'd: ${it.message}...sending to server")
    }
    println("event stream closed")
}


// example client code
fun main() {
    val service = FooService()
    val input = PublishMessagesInput().apply {room = "foo room", capacity = 5}
    runBlocking {
        launch {
            val metadata = sendInitialMessage(input)
            if(metadata != null) {
                repeat(5) {
                    input.messages.add(Message().apply{message = "message: $it"})
                }

                service.publishMessages(input)
            }
        }
    }
}

```

### Initial Request
An initial-request is an initial message that can be sent from a client to a server for an operation with an input event stream. The structure of an initial-request is the input of an operation with no value provided for the event stream member. An initial-request, if sent, is sent from a client to a server before sending any event stream events.

When using HTTP bindings, initial-request fields are mapped to specific locations in the HTTP request such as headers or the URI. In other bindings or protocols, the initial-request can be sent however is necessary for the protocol.

The following example defines an operation with an input event stream with an initial-request. The client will first send the initial-request to the service, followed by the events sent in the payload of the HTTP message.

```
namespace smithy.example

@http(method: "POST", uri: "/messages/{room}")
operation PublishMessages {
    input: PublishMessagesInput
}

structure PublishMessagesInput {
    @httpLabel
    room: String,

    @httpPayload
    @eventStream
    messages: Message,
}

structure Message {
    message: String,
}
```

This can be represented in kotlin like so:
```kotlin
//generated code for client
class PublishMessagesInput constructor(capacity: Int = Channel.RENDEZVOUS) {
    var room: String? = null
    var messages: List<Message> = emptyList()
    val capacity = capacity
}

class Message {
    var message: String? = null
}


class FooService {
   
    suspend fun publishMessages(input: PublishMessagesInput): SendChannel<PublishMessagesInput>> {
        //send initial request to server before any messages are sent
        sendInitialRequest(input)

        for (msg in input.messages){
        consumer.send(msg)
        }
        consumer.close()
    }
     /// method: POST, uri: "/messages/{room}"
    suspend fun sendInitialRequest(input: PublishMessagesInput) {
        //sends intial request to the server with room property
        // in this case the property is room that doesnt have the event trait so send `input.room` to server and return metadata.
        httpClient.post(baseurl + "/messages/%.2f".format(input.room))
    }

}

// mock server side
private val consumer = consume<Message>(input.capacity) {
    consumeEach {
        println("recv'd: ${it.message}...sending to server")
    }
    println("event stream closed")
}


// example client code
fun main() {
    val service = FooService()
    val input = PublishMessagesInput().apply {room = "foo room", capacity = 5}
    runBlocking {
        launch {
            sendInitialRequest(input)
        }
        launch {
            repeat(5) {
                input.messages.add(Message().apply{message = "message: $it"})
            }

            service.publishMessages(input)
        }
    }
}
```

In the case of using web sockets in this scenario, the intitial request would open the web socket.

### Initial Response
An initial-response is an initial message that can be sent from a server to a client for an operation with an output event stream. The structure of an initial-response is the output of an operation with no value provided for the event stream member. An initial-response, if sent, is sent from the server to the client before sending any event stream events.

When using HTTP bindings, initial-response fields are mapped to HTTP headers. In other protocols, the initial-response can be sent however is necessary for the protocol.

The following example defines an operation with an output event stream with an initial-response. The client will first receive and process the initial-response, followed by the events sent in the payload of the HTTP message.

```
namespace smithy.example

@http(method: "GET", uri: "/messages/{room}")
operation SubscribeToMessages {
    input: SubscribeToMessagesInput,
    output: SubscribeToMessagesOutput
}

structure SubscribeToMessagesInput {
    @httpLabel
    room: String
}

structure SubscribeToMessagesOutput {
    @httpHeader("X-Connection-Lifetime")
    connectionLifetime: Integer,

    @httpPayload
    @eventStream
    messages: Message,
}
```

This can be represented in kotlin like so:
```kotlin

class SubscribeToMessagesOutput {
    var messages: Message
    var connectionLifetime: Int

class Message {
    var message: String
}

class SubscribeToMessagesInput {
    var room: String
}


class FooService {

    fun processInitialResponse(input: SubscribeToMessageInput): Bool {
        //send request to server using http label from input in uri and get initial response back containing connectionLifetime
    }

    fun CoroutineScope.subscribeToMessages(): ReceiveChannel<SubscribeToMessagesOutput>> = produce {
        // real implementation would setup the network connection and wire it to the send channel
        //use http label from input to add to the uri for the request
        // here we fake it with a function that produces movements as if it were the server
        launch { produceMovements(it) }
    }

}

// mock server side
private fun produceMovements(ch: SendChannel<Movement>) {
    repeat(5) {
        ch.send(Movement())
    }
}


//to use this generated code in code as a developer you might do something like below:

fun main() {
    val service = FooService()
    val gotResponse = service.processInitialResponse() //if http the response is in the header and not sure if we need it?
    runBlocking {
        if (gotResponse) {
            val movements = service.subscribeToMovements()
            movements.consumeEach {println(it)}
        }
    }
}

```

In the case of web sockets the intial response might return the status of the web socket aka open, closed, error, etc
