/*
// Simplex event stream showing initial-request with a client publishing messages to a service
// NOTE: Some types are suffixed with a number to not conflict with other examples that have same names

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

*/


package com.amazonaws.smithy.poc

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

// library code
class PublishEventStream<T> (private val channel: Channel<T>) {
    suspend fun publish(block: suspend SendChannel<T>.() -> Unit) {
        channel.block()
        channel.close()
    }
}

// generated code for client
class PublishMessagesInput {
    var room: String? = null
}

class Message1 {
    var message: String? = null
}


class FooService1 {

    // Considerations:
    // 1. Why take a scope parameter? - To take advantage of structured concurrency. This function needs the ability to launch a new coroutine to babysit
    // the messages coming from the client and then push them onto the wire to the server/service
    // see: https://medium.com/@elizarov/coroutine-context-and-scope-c8b255d59055
    //
    // 2. Why allow setting channel capacity? - There is no default that will satisfy every situation. This allows the caller to decide what kind of concurrency
    // makes sense for their application.
    suspend fun publishMessages(scope: CoroutineScope, input: PublishMessagesInput, capacity: Int = Channel.RENDEZVOUS): PublishEventStream<Message1> {
        // send initial-request
        // sendRequest(input)
        val channel: Channel<Message1> = Channel<Message1>(capacity)
        scope.launch {
            consumeMessages(channel)
        }

        return PublishEventStream<Message1>(channel)
    }

    private suspend fun consumeMessages(chan: Channel<Message1>) {
        chan.consumeEach {
            println("recv'd: ${it.message}; sending to server...")
        }
        println("consumer done")
    }
}


// example client code
fun simpleInputStream() {
    val service = FooService1()
    val input = PublishMessagesInput().apply {room = "foo room"}
    runBlocking {
        val es = service.publishMessages(this, input)
        es.publish {
            repeat(5) {
                println("sending message: $it")
                send(Message1().apply{message = "msg $it"})
            }
        }
    }
}
