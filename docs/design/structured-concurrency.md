# Structured Concurrency

This document gives an overview of the [CoroutineScope](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-scope/)(s) 
used by the SDK.

## Definitions

* **Coroutine** - An instance of a suspendable computation. A coroutine itself is represented by a Job. It is responsible for coroutine’s lifecycle, cancellation, and parent-child relations.
* **CoroutineContext** - Persistent (immutable) context for a coroutine
* **CoroutineScope** - Defines a scope for new coroutines which delimits the lifetime of the coroutine
* **Structured Concurrency** - A principle expected by coroutines (in Kotlin) whereby new coroutines can only be launched into a specific CoroutineScope. This constraint ensures that coroutines are not leaked and that errors are propagated correctly. An outer scope cannot complete until all its child coroutines complete. 
* **HttpCall** - A single HTTP request/response pair. 
* **HttpClientEngine** - A component responsible for executing an HTTP request and returning an HttpCall
* **ExecutionContext** - Operation scoped (mutable) context used to drive an operation to completion


## Scopes

The SDK has three implementations of `CoroutineScope`:

1. [ExecutionContext](https://github.com/awslabs/smithy-kotlin/blob/main/runtime/runtime-core/common/src/aws/smithy/kotlin/runtime/operation/ExecutionContext.kt#L16)
2. [HttpClientEngine](https://github.com/awslabs/smithy-kotlin/blob/main/runtime/protocol/http-client/common/src/aws/smithy/kotlin/runtime/http/engine/HttpClientEngine.kt#L20)
3. [HttpCall](https://github.com/awslabs/smithy-kotlin/blob/main/runtime/protocol/http/common/src/aws/smithy/kotlin/runtime/http/response/HttpCall.kt#L28)
    

`ExecutionContext` implements `CoroutineScope` to provide a place for any background work to be done as part of implementing an operation. 
Its scope begins and ends with an operation. The only place it is utilized is to launch background task(s) to process input event streams 
(pull data from the customer input event stream, transform it, and write it to the HTTP body). 

`HttpClientEngine` implements `CoroutineScope` and is used to launch HTTP requests. The scope of the engine is from creation to when it is closed.
Making HTTP request tasks children of this scope means that an engine won’t be shutdown until in-flight requests are complete.
The engine scope is a [SupervisorJob](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-supervisor-job.html) such that individual HTTP requests can fail independent of one another without causing all requests to be cancelled.

`HttpCall` implements `CoroutineScope` to provide a place for any background work required to fulfill an HTTP request/response. It is described
in more detail in the following section.

### HttpCall Scope

Individual HTTP calls get their own `CoroutineContext` whose scope begins with executing the request and ends when `HttpCall::complete()` is invoked.


![HttpCall Context](resources/HttpCall-Context.png)
