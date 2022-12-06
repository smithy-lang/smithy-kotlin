# Operation Interceptors Design

* **Type**: Design
* **Author(s)**: Aaron Todd, Reference Architecture

# Abstract

This document covers the design and implementation of the API for extending how
the SDK executes an operation. This extension is referred to as an *interceptor*. 
An interceptor allows a customer to hook into specific stages of execution within 
an SDK such as serialization or signing. It allows extending those stages by modifying 
their inputs or outputs. Interceptors are registered during code generation or runtime
through client configuration or runtime plugins.

# Design

## Terminology 

* **Execution** - one end-to-end invocation against an SDK client

* **Attempt** - a single attempt at performing an execution, executions may be retired multiple times 
based on the client's retry strategy

* **Hook** - a specific point during the execution of an operation that an interceptor can 
be notified about. Hooks are either **read-only** or **read/write**. **Read-only** hooks
allow an interceptor to read the operation input, protocol request, protocol response,
and/or the operation output. **Read/write** hooks allow an interceptor to modify
one of these entities. 


## Client Interface

The set of hooks is defined in the interface below with descriptions of when they are executed, what information is available,
as well as how errors are handled.

Each interceptor hook can:
• Read the messages generated so far during in the execution
• Save information to a context object that is available across all hooks
• (Write Hooks Only) Modify the input message, transport request message, transport response message or output 


```kotlin

/**
 * An interceptor allows injecting code into the request execution pipeline of a generated SDK client.
 *
 * Terminology:
 * * `execution` - one end-to-end invocation against an SDK client
 * * `attempt` - a single attempt at performing an execution, executions may be retired multiple times
 * based on the client's retry strategy.
 * * `hook` - a single method on the interceptor allowing injection of code into a specific part of the execution
 * pipeline. Hooks are either "read-only" hooks, which make it possible to read in-flight request or response messages,
 * or `read/write` hooks, which make it possible to modify in-flight request or response messages. Read only hooks
 * **MUST** not modify state even if it is possible to do so (it is not always possible or performant to provide an
 * immutable view of every type).
 */
public interface Interceptor<
    Input,
    Output,
    ProtocolRequest,
    ProtocolResponse,
    > {

    /**
     * A hook called at the start of an execution, before the SDK does anything else.
     *
     * **When**: This will ALWAYS be called once per execution. The duration between
     * invocation of this hook and [readAfterExecution] is very close to full duration of the execution
     *
     * **Available Information**: [RequestInterceptorContext.request] is always available.
     *
     * **Error Behavior**: Errors raised by this hook will be stored until all interceptors have had their
     * `readBeforeExecution` hook invoked. Other hooks will then be skipped and execution will jump to
     * [modifyBeforeCompletion] with the raised error as the [ResponseInterceptorContext.response] result. If
     * multiple `readBeforeExecution` hooks raise errors, the latest will be used and earlier
     * ones will be logged and added as suppressed exceptions.
     */
    public fun readBeforeExecution(context: RequestInterceptorContext<Input>) {}

    /**
     * A hook called before the input message is marshalled into a (protocol) transport message.
     * This method has the ability to modify and return a new operation request.
     *
     * **When**: This will ALWAYS be called once per execution, except when a failure occurs earlier in the
     * request pipeline.
     *
     * **Available Information**: [RequestInterceptorContext.request] is ALWAYS available. This request may have been
     * modified by earlier [modifyBeforeSerialization] hooks, and may be modified further by later ones.
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [modifyBeforeCompletion]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun modifyBeforeSerialization(context: RequestInterceptorContext<Input>): Input = context.request

    /**
     * A hook called before the input message is marshalled into a (protocol) transport message.
     *
     * **When**: This will ALWAYS be called once per execution, except when a failure occurs earlier in the
     * request pipeline. The duration between invocation of this hook and [readAfterSerialization] is very
     * close to the amount of time spent marshalling the request.
     *
     * **Available Information**: [RequestInterceptorContext.request] is ALWAYS available.
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [modifyBeforeCompletion]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun readBeforeSerialization(context: RequestInterceptorContext<Input>) {}

    /**
     * A hook called after the input message is marshalled into a (protocol) transport message.
     *
     * **When**: This will ALWAYS be called once per execution, except when a failure occurs earlier in the
     * request pipeline. The duration between invocation of this hook and [readBeforeDeserialization] is very
     * close to the amount of time spent marshalling the request.
     *
     * **Available Information**: [RequestInterceptorContext.request] and
     * [ProtocolRequestInterceptorContext.protocolRequest] are ALWAYS available.
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [modifyBeforeCompletion]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun readAfterSerialization(context: ProtocolRequestInterceptorContext<Input, ProtocolRequest>) {}

    /**
     * A hook called before the retry loop is entered. This method has the ability to modify and return a new
     * transport request.
     *
     * **When**: This will ALWAYS be called once per execution, except when a failure occurs earlier in the
     * request pipeline.
     *
     * **Available Information**: [RequestInterceptorContext.request] and
     * [ProtocolRequestInterceptorContext.protocolRequest] are ALWAYS available.
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [modifyBeforeCompletion]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun modifyBeforeRetryLoop(
        context: ProtocolRequestInterceptorContext<Input, ProtocolRequest>,
    ): ProtocolRequest =
        context.protocolRequest

    /**
     * A hook called before each attempt at sending the protocol request message to the service.
     *
     * **When**: This will ALWAYS be called once per _attempt_, except when a failure occurs earlier in the
     * request pipeline. This method may be invoked multiple times in the event of retries.
     *
     * **Available Information**: [RequestInterceptorContext.request] and
     * [ProtocolRequestInterceptorContext.protocolRequest] are ALWAYS available. In the event of retries,
     * the context will not include changes made in previous attempts (e.g. by request signers or other interceptors).
     *
     * **Error Behavior**: Errors raised by this hook will be stored until all interceptors have had their
     * `readBeforeAttempt` hook invoked. Other hooks will then be skipped and execution will jump to
     * [modifyBeforeAttemptCompletion] with the raised error as the [ResponseInterceptorContext.response] result.
     * If multiple interceptors raise an error in `readBeforeAttempt` then the latest will be used and earlier
     * ones will be logged and added as suppressed exceptions.
     */
    public fun readBeforeAttempt(context: ProtocolRequestInterceptorContext<Input, ProtocolRequest>) {}

    /**
     * A hook called before the transport request message is signed. This method has the ability to modify and
     * return a new transport request.
     *
     * **When**: This will ALWAYS be called once per _attempt_, except when a failure occurs earlier in the
     * request pipeline. This method may be called multiple times in the event of retries.
     *
     * **Available Information**: [RequestInterceptorContext.request] and
     * [ProtocolRequestInterceptorContext.protocolRequest] are ALWAYS available. The
     * [ProtocolRequestInterceptorContext.protocolRequest] may have been modified by earlier `modifyBeforeSigning` hooks
     * and may be modified further by later hooks. In the event of retries, the context will not include changes made
     * in previous attempts (e.g. by request signers or other interceptors).
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [modifyBeforeAttemptCompletion]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun modifyBeforeSigning(
        context: ProtocolRequestInterceptorContext<Input, ProtocolRequest>,
    ): ProtocolRequest =
        context.protocolRequest

    /**
     * A hook called before the transport request message is signed.
     *
     * **When**: This will ALWAYS be called once per _attempt_, except when a failure occurs earlier in the
     * request pipeline. This method may be called multiple times in the event of retries. The duration between
     * invocation of this hook and [readAfterSigning] is very close to the amount of time spent signing the request.
     *
     * **Available Information**: [RequestInterceptorContext.request] and
     * [ProtocolRequestInterceptorContext.protocolRequest] are ALWAYS available. In the event of retries, the context
     * will not include changes made in previous attempts (e.g. by request signers or other interceptors).
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [modifyBeforeAttemptCompletion]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun readBeforeSigning(context: ProtocolRequestInterceptorContext<Input, ProtocolRequest>) {}

    /**
     * A hook called after the transport request message is signed.
     *
     * **When**: This will ALWAYS be called once per _attempt_, except when a failure occurs earlier in the
     * request pipeline. This method may be called multiple times in the event of retries. The duration between
     * invocation of this hook and [readBeforeSigning] is very close to the amount of time spent signing the request.
     *
     * **Available Information**: [RequestInterceptorContext.request] and
     * [ProtocolRequestInterceptorContext.protocolRequest] are ALWAYS available. In the event of retries, the context
     * will not include changes made in previous attempts (e.g. by request signers or other interceptors).
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [modifyBeforeAttemptCompletion]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun readAfterSigning(context: ProtocolRequestInterceptorContext<Input, ProtocolRequest>) {}

    /**
     * A hook called before the transport request message is sent to the service. This method has the ability to modify
     * and return a new transport request.
     *
     * **When**: This will ALWAYS be called once per _attempt_, except when a failure occurs earlier in the
     * request pipeline. This method may be called multiple times in the event of retries.
     *
     * **Available Information**: [RequestInterceptorContext.request] and
     * [ProtocolRequestInterceptorContext.protocolRequest] are ALWAYS available. The
     * [ProtocolRequestInterceptorContext.protocolRequest] may have been modified by earlier `modifyBeforeSigning` hooks
     * and may be modified further by later hooks. In the event of retries, the context will not include changes made
     * in previous attempts (e.g. by request signers or other interceptors).
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [modifyBeforeAttemptCompletion]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun modifyBeforeTransmit(
        context: ProtocolRequestInterceptorContext<Input, ProtocolRequest>,
    ): ProtocolRequest =
        context.protocolRequest

    /**
     * A hook called before the transport request message is sent to the service.
     *
     * **When**: This will ALWAYS be called once per _attempt_, except when a failure occurs earlier in the
     * request pipeline. This method may be called multiple times in the event of retries. The duration between
     * invocation of this hook and [readAfterTransmit] is very close to the amount of time it took to send
     * a request and receive a response from the service.
     *
     * **Available Information**: [RequestInterceptorContext.request] and
     * [ProtocolRequestInterceptorContext.protocolRequest] are ALWAYS available. In the event of retries, the context
     * will not include changes made in previous attempts (e.g. by request signers or other interceptors).
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [modifyBeforeAttemptCompletion]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun readBeforeTransmit(context: ProtocolRequestInterceptorContext<Input, ProtocolRequest>) {}

    /**
     * A hook called after the transport response message is received from the service.
     *
     * **When**: This will ALWAYS be called once per _attempt_, except when a failure occurs earlier in the
     * request pipeline. This method may be called multiple times in the event of retries. The duration between
     * invocation of this hook and [readBeforeTransmit] is very close to the amount of time it took to send
     * a request and receive a response from the service.
     *
     * **Available Information**: [RequestInterceptorContext.request],
     * [ProtocolRequestInterceptorContext.protocolRequest], and [ProtocolResponseInterceptorContext.protocolResponse]
     * are ALWAYS available. In the event of retries, the context will not include changes made in previous attempts
     * (e.g. by request signers or other interceptors).
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [modifyBeforeAttemptCompletion]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun readAfterTransmit(context: ProtocolResponseInterceptorContext<Input, ProtocolRequest, ProtocolResponse>) {}

    /**
     * A hook called before the transport request message is deserialized into the output response type.
     * This method has the ability to modify and return a new transport response.
     *
     * **When**: This will ALWAYS be called once per _attempt_, except when a failure occurs earlier in the
     * request pipeline. This method may be called multiple times in the event of retries.
     *
     * **Available Information**: [RequestInterceptorContext.request],
     * [ProtocolRequestInterceptorContext.protocolRequest], and [ProtocolResponseInterceptorContext.protocolResponse]
     * are ALWAYS available. The [ProtocolResponseInterceptorContext.protocolResponse] may have been modified by earlier
     * `modifyBeforeDeserialization` hooks and may be modified further by later hooks. In the event of retries, the
     * context will not include changes made in previous attempts (e.g. by request signers or other interceptors).
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [modifyBeforeAttemptCompletion]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun modifyBeforeDeserialization(
        context: ProtocolResponseInterceptorContext<Input, ProtocolRequest, ProtocolResponse>,
    ): ProtocolResponse =
        context.protocolResponse

    /**
     * A hook called before the transport request message is deserialized into the output response type.
     *
     * **When**: This will ALWAYS be called once per _attempt_, except when a failure occurs earlier in the
     * request pipeline. This method may be called multiple times in the event of retries. The duration between
     * invocation of this hook and [readAfterDeserialization] is very close to the amount of time spent deserializing
     * the protocol response into the modeled operation response.
     *
     * **Available Information**: [RequestInterceptorContext.request],
     * [ProtocolRequestInterceptorContext.protocolRequest], and [ProtocolResponseInterceptorContext.protocolResponse]
     * are ALWAYS available. In the event of retries, the context will not include changes made in previous attempts
     * (e.g. by request signers or other interceptors).
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [modifyBeforeAttemptCompletion]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun readBeforeDeserialization(context: ProtocolResponseInterceptorContext<Input, ProtocolRequest, ProtocolResponse>) {}

    /**
     * A hook called after the transport request message is deserialized into the output response type.
     *
     * **When**: This will ALWAYS be called once per _attempt_, except when a failure occurs earlier in the
     * request pipeline. This method may be called multiple times in the event of retries. The duration between
     * invocation of this hook and [readBeforeDeserialization] is very close to the amount of time spent deserializing
     * the protocol response into the modeled operation response.
     *
     * **Available Information**: [RequestInterceptorContext.request],
     * [ProtocolRequestInterceptorContext.protocolRequest], [ProtocolResponseInterceptorContext.protocolResponse],
     * and [ResponseInterceptorContext.response] are ALWAYS available. In the event of retries, the context will not
     * include changes made in previous attempts (e.g. by request signers or other interceptors).
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [modifyBeforeAttemptCompletion]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun readAfterDeserialization(context: ResponseInterceptorContext<Input, Output, ProtocolRequest, ProtocolResponse>) {}

    /**
     * A hook called when an attempt is completed. This method has the ability to modify and return a new operation
     * output or error.
     *
     * **When**: This will ALWAYS be called once per _attempt_, except when a failure occurs before [readBeforeAttempt]
     * This method may be called multiple times in the event of retries.
     *
     * **Available Information**: [RequestInterceptorContext.request],
     * [ProtocolRequestInterceptorContext.protocolRequest], [ProtocolResponseInterceptorContext.protocolResponse]
     * are ALWAYS available. [ResponseInterceptorContext.response] is available if execution made it that far.
     * In the event of retries, the context will not include changes made in previous attempts
     * (e.g. by request signers or other interceptors).
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [readAfterAttempt]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun modifyBeforeAttemptCompletion(context: ResponseInterceptorContext<Input, Output, ProtocolRequest, ProtocolResponse?>): Result<Output> =
        context.response

    /**
     * A hook called when an attempt is completed.
     *
     * **When**: This will ALWAYS be called once per _attempt_, as long as [readBeforeAttempt] was executed.
     *
     * **Available Information**: [RequestInterceptorContext.request],
     * [ProtocolRequestInterceptorContext.protocolRequest], and [ResponseInterceptorContext.response] are ALWAYS
     * available. [ProtocolResponseInterceptorContext.protocolResponse] is available if a response was received
     * by the service for this attempt. In the event of retries, the context will not include changes made in previous
     * attempts (e.g. by request signers or other interceptors).
     *
     * **Error Behavior**: Errors raised by this hook will be stored until all interceptors have had their
     * `readAfterAttempt` hook invoked. If multiple interceptors raise an error in `readAfterAttempt` then the latest
     * will be used and earlier ones will be logged and added as suppressed exceptions. If the result of the attempt
     * is determined to be retryable then execution will jump to [readBeforeAttempt]. Otherwise, execution will jump
     * to [modifyBeforeCompletion] with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun readAfterAttempt(context: ResponseInterceptorContext<Input, Output, ProtocolRequest, ProtocolResponse?>) {}

    /**
     * A hook called when an attempt is completed. This method has the ability to modify and return a new operation
     * output or error.
     *
     * **When**: This will ALWAYS be called once per execution.
     *
     * **Available Information**: [RequestInterceptorContext.request] and [ResponseInterceptorContext.response]
     * are ALWAYS available. [ProtocolRequestInterceptorContext.protocolRequest] and
     * [ProtocolResponseInterceptorContext.protocolResponse] are available if the execution proceeded far enough for
     * them to be generated.
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [readAfterExecution]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun modifyBeforeCompletion(context: ResponseInterceptorContext<Input, Output, ProtocolRequest?, ProtocolResponse?>): Result<Output> =
        context.response

    /**
     * A hook called when an attempt is completed.
     *
     * **When**: This will ALWAYS be called once per execution. The duration between invocation of this hook
     * and [readBeforeExecution] is very close to the full duration of the execution.
     *
     * **Available Information**: [RequestInterceptorContext.request] and [ResponseInterceptorContext.response]
     * are ALWAYS available. [ProtocolRequestInterceptorContext.protocolRequest] and
     * [ProtocolResponseInterceptorContext.protocolResponse] are available if the execution proceeded far enough for
     * them to be generated.
     *
     * **Error Behavior**: Errors raised by this hook will be stored until all interceptors have had their
     * `readAfterExecution` hook invoked. If multiple interceptors raise an error in `readAfterExecution` then the
     * latest will be used and earlier ones will be logged and added as suppressed exceptions. The error will then
     * be treated as the final response to the caller.
     */
    public fun readAfterExecution(context: ResponseInterceptorContext<Input, Output, ProtocolRequest?, ProtocolResponse?>) {}
}

/**
 * [Interceptor] context used for all phases that only have access to the operation input (request)
 */
public interface RequestInterceptorContext<I> : Attributes {

    /**
     * Retrieve the modeled request for the operation being invoked
     */
    public val request: I
}

/**
 * [Interceptor] context used for all phases that have access to the operation input (request) and the
 * serialized protocol specific request (e.g. HttpRequest).
 */
public interface ProtocolRequestInterceptorContext<I, ProtocolRequest> : RequestInterceptorContext<I> {
    /**
     * Retrieve the protocol specific request for the operation being invoked.
     */
    public val protocolRequest: ProtocolRequest
}

/**
 * [Interceptor] context used for all phases that have access to the operation input (request), the
 * serialized protocol specific request (e.g. HttpRequest), and the protocol specific response (e.g. HttpResponse).
 */
public interface ProtocolResponseInterceptorContext<I, ProtocolRequest, ProtocolResponse> :
    ProtocolRequestInterceptorContext<I, ProtocolRequest> {
    /**
     * Retrieve the protocol specific response for the operation being invoked.
     */
    public val protocolResponse: ProtocolResponse
}

/**
 * [Interceptor] context used for all phases that have access to the operation input (request), the
 * serialized protocol specific request (e.g. HttpRequest), the protocol specific response (e.g. HttpResponse),
 * and the deserialized operation output (response).
 */
public interface ResponseInterceptorContext<I, O, ProtocolRequest, ProtocolResponse> :
    ProtocolResponseInterceptorContext<I, ProtocolRequest, ProtocolResponse> {
    /**
     * Retrieve the modeled response or exception for the operation being invoked
     */
    public val response: Result<O>
}

```

The `Interceptor` interface defined above is generic over:
* `Input` - the operation input type
* `Output` - the operation output type
* `ProtocolRequest` - the specific protocol (transmit) request type
* `ProtocolResponse` - the specific protocol (transmit) response type


Observations:

* Every interceptor method has a default implementation. This allows customers to override only the hooks they are interested in as well
as allowing future hooks to be defined without breaking API or binary compatibility.
* Multiple context types are defined that restrict the set of information available to a particular hook (e.g. you can't access the operation'output in any hook until *after* the first deserialization hook). Additionally the hooks are specific about the nullability of particular context
information (e.g. the `modifyBeforeCompletion` hook may or may not have a protocol request or response available depending on how far the
operation execution made it.
* Hooks are non-blocking (note no `suspend` in any hook method) and implementators are expected to respect this
* Despite being generic over the input and output type, the `Input` and `Output` types of an `Interceptor` are expected to be `Any` in practice.
This is because interceptors can be registered at the *client* level. Such an interceptor would be executed for every operation and
would not be able to know the specific input or output type at compile time. Interceptors that execute for any operation are common and
expected (e.g. add a header to every outgoing request). The downside to this flexibility is that customers will have to cast the input or
output type from the context to the specific operation type. 
    * NOTE: The modeled operation name will be available in the context (it is already available in the execution context) which can be used
    on interceptors registered at the client level that want to deal with specific operations.


### HTTP Interceptor


The `Interceptor` interface defined above would not be useful to a customer without specific types. Each protocol will get it's own
`typealias` that fills in the generics that make sense for a particular protocol. For instance HTTP based protocol clients would
be generated to reference the more specific interceptor type:

```kotlin
typealias HttpInterceptor = Interceptor<Any, Any, HttpRequest, HttpResponse>
```


#### Alternative 1

Alternatively the `Interceptor` interface could hard code `Any` for `Input` and `Output` types. This would simplify some of the runtime
type definitions by removing the propagation of those generics. The end result to the customer would be the same (a protocol specific
interceptor `typealias` is still expected). 


## Interceptor Registration

TODO

## Interceptor Priority

Interceptors may be registered directly or via runtime plugins (TBD). Interceptors will be registered and executed in a deterministic order:

• Interceptors registered via. smithy default plugins
• (AWS Services) Interceptors registered via AWS default plugins (AWS SDKs only)
• Interceptors registered via. service-customization plugins
• Interceptors registered via. client-level plugins
• Interceptors registered via. client-level configuration
• Interceptors registered via. operation-level plugins
• Interceptors registered via operation-level configuration


## Example Usage

The following example shows how an interceptor could add a header to every outgoing (HTTP) request:

```kotlin

class AddHeaderInterceptor : HttpInterceptor {
    override fun modifyBeforeSigning(
        context: ProtocolRequestInterceptorContext<Any, HttpRequest>
    ): HttpRequest {
        val builder = context.protocolRequest.toBuilder()
        builder.headers.append("my-header", "foobar")
        return builder.build()
    }

}

```


The following example shows how an interceptor could default an operation input member if not set:

For the sake of this example lets pretend the operation input is named `FooInput` with an optional `String` member
named `bar`. Let's also assume that this interceptor is registered only for a specific operation (i.e. not at the client level).


```kotlin

class DefaultFooOperationMemberInterceptor : HttpInterceptor {
    override fun modifyBeforeSerialization(
        context: RequestInterceptorContext<Any>
    ): Any {
        val input = context.request as? FooInput ?: error("expected type FooInput")
        return if (input.bar == null) {
            return input.copy {
                bar = "my default value"
            }
        }else {
            // just use the current input without modification
            input
        }
    }
}

```



# Appendix

## FAQ

**Why do interceptors have separate read and read/write hooks?**

If all hooks were read/write (modify) hooks, then any interceptor invoked before "later" interceptors would not necessarily
get the actual value the SDK sees for a particular phase. If all hooks were read only hooks, then the value of interceptors would
be greatly diminished.


**The SDK has an internal middleware stack with predefined phases already with capabilities similar to the proposed interface. Why not expose that directly instead?**

Middleware is a good abstraction for the internals of the SDK but it is too flexible to safely reason about as the public abstraction. Ordering
of middleware is more loosely defined and not guaranteed. It is also allowed to `suspend` and do any number of other things we don't
necessarily want a user to be doing (e.g. defining a custom retry layer as opposed to using the provided abstractions).

Finally it is desirable to have a stable external abstraction while still being able to modify the internals of the SDK as necessary in the future. The separation of the two allows for this evolution.

**Should existing internal middleware components be re-written as interceptors**?

No. While some of the existing middleware may be possible to convert to an equivalent interceptor there is no reason to do so. Middleware isn't
going anywhere and is the preferred internal abstraction for customizing SDK behavior. 

**Why not add additional generics to handle mutablity of input/output types?**

Input and output types of an operation are already immutable (see [Kotlin Smithy SDK](kotlin-smithy-sdk.md)) and require an explicit `copy`
to mutate. The assumption of immutability of these types is baked into the interface.


## Implementation Details

### Retry and Signing Middleware

Every hook defined in the `Interceptor` interface can be implemented as part of [SdkOperationExecution](TODO) with the exception of the
retry (attempt) and signing related hooks. These two capabilities are currently implemented in terms of [Middleware](TODO). The anticipation 
is that these middleware components will be pulled into `SdkOperationExecution` as explicit phases (similar to serialization and 
deserialization). The middleware as it exists today will be removed and relocated to these new explicit phases. This should be safe to do as 
every operation typically has a retry and signing middleware and those that don't can have no-op implementations. 

The alternative would be to pass around interceptors to these mdidleware components. This works but has the downside of making it harder
to understand and see all the interceptor hook points in one place. It will also be harder to guarantee hook ordering since middleware 
phases are ordered but the order within a phase is depeendent on *when* a middelware is registered for an operation. Making the phases
explicit will guarantee the order.


## Progress Listeners

There has been some customer [demand](TODO) for the ability to implement a progress listener abstraction on top of an SDK operation.
This is *sort of* possible today by wrapping an operation input or output `ByteStream` type and responding to when data is read from the
stream. This has huge footguns though as it is not well defined when or how many times a stream will be consumed (e.g. AWS SigV4 signing
typically consumes a stream to calculate the payload hash. The payload is then consumed a second time when being sent out on the wire to
the remote service). 

The ability to implement a progress listener will be enabled by [per operation config](TODO) and interceptors.

A user wishing to monitor upload progress would simply need to wrap the outgoing response body in the `modifyBeforeTransmit` hook (which
is guaranteed to be the last hook before the request is transmitted). A download progress listener could similarly be implemented 
in `modifyBeforeDeserialization`. 


Example upload listener

NOTE: It is assumed this is registered per/operation as opposed to the client level.

```kotlin

class AddUploadProgressListenerInterceptor(
    private val progressListener: ProgressListener
): HttpInterceptor {
    override fun modifyBeforeTransmit(
        context: ProtocolRequestInterceptorContext<Any, HttpRequest>
    ): HttpRequest {
        val builder = context.protocolRequest.toBuilder()
        // wrap the body with a custom type that updates `progressListener` every time data is read
        builder.body = ProgressListenerBody(builder.body, progressListener)
        return builder.build()
    }
}

```

NOTE: A content length is required to implement a meaningful progress listener. Customers will likely need to disable `aws-chunked` signing and
any other SDK abstraction that results in `Transfer-Encoding: chunked` with no concrete stream length.

## Additional References

TODO

# Revision history

* 12/04/2022 - Created
