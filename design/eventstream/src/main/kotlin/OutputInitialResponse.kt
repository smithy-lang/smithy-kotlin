/*
// Simplex output event stream with an initial-response
// NOTE: Some types are suffixed with a number to not conflict with other examples that have same names
// See: https://awslabs.github.io/smithy/spec/event-streams.html#initial-response

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

*/
package com.amazonaws.smithy.poc

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

// generated code for client
class SubscribeToMessagesInput {
    var room: String? = null
}

class Message2 {
    var message: String? = null
}

class SubscribeToMessagesOutput (channel: ReceiveChannel<Message2>){
    var connectionLifetime: Int = 0

    var messages: ReceiveChannel<Message2> = channel
}


class FooService2 {
    suspend fun subscribeToMessages(scope: CoroutineScope, input: SubscribeToMessagesInput, capacity: Int = Channel.RENDEZVOUS): SubscribeToMessagesOutput {
        // send initial-request
        // sendRequest(input)

        // simulate getting the initial response
        delay(1000)

        val channel = Channel<Message2>(capacity)
        var resp = SubscribeToMessagesOutput(channel)
        // launch coroutine to wait for incoming messages and propagate them to the client
        scope.launch {
            receiveMessages(channel)
        }

        return resp
    }

    internal suspend fun receiveMessages(chan: Channel<Message2>) {
        // pull messages off the wire and send them to the client
        repeat(5) {
            val msg = Message2().apply { message = "Message $it"}
            println("sending: ${msg.message}")
            chan.send(msg)
        }

        chan.close()
        println("server done")
    }
}



// example client code
fun simpleOutputStream() {
    val service = FooService2()
    runBlocking {
        val input = SubscribeToMessagesInput().apply {room = "foo room"}

        val resp = service.subscribeToMessages(this, input)
        for (msg in resp.messages) {
            println("received message: ${msg.message}")
        }
    }
}
