/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.client

import aws.smithy.kotlin.runtime.util.Attributes


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
 * MUST not modify state even if it is possible to do so (it is not always possible or performant to provide an
 * immutable view of every type).
 */
public interface Interceptor<I, O, TReq, TResp> {

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
    public fun readBeforeExecution(context: RequestInterceptorContext<I>): Unit {}

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
    public fun modifyBeforeSerialization(context: RequestInterceptorContext<I>): I = context.request

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
    public fun readBeforeSerialization(context: RequestInterceptorContext<I>): Unit {}

    /**
     * A hook called after the input message is marshalled into a (protocol) transport message.
     *
     * **When**: This will ALWAYS be called once per execution, except when a failure occurs earlier in the
     * request pipeline. The duration between invocation of this hook and [readBeforeDeserialization] is very
     * close to the amount of time spent marshalling the request.
     *
     * **Available Information**: [RequestInterceptorContext.request] and
     * [ProtocolRequestInterceptorContext.transmitRequest] are ALWAYS available.
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [modifyBeforeCompletion]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun readAfterSerialization(context: ProtocolRequestInterceptorContext<I, TReq>): Unit {}

    /**
     * A hook called before the retry loop is entered. This method has the ability to modify and return a new
     * transport request.
     *
     * **When**: This will ALWAYS be called once per execution, except when a failure occurs earlier in the
     * request pipeline.
     *
     * **Available Information**: [RequestInterceptorContext.request] and
     * [ProtocolRequestInterceptorContext.transmitRequest] are ALWAYS available.
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [modifyBeforeCompletion]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun modifyBeforeRetryLoop(context: ProtocolRequestInterceptorContext<I, TReq>): TReq =
        checkNotNull(context.transmitRequest) { "modifyBeforeRetryLoop: transmitRequest must not be null" }

    /**
     * A hook called before each attempt at sending the protocol request message to the service.
     *
     * **When**: This will ALWAYS be called once per _attempt_, except when a failure occurs earlier in the
     * request pipeline. This method may be invoked multiple times in the event of retries.
     *
     * **Available Information**: [RequestInterceptorContext.request] and
     * [ProtocolRequestInterceptorContext.transmitRequest] are ALWAYS available. In the event of retries,
     * the context will not include changes made in previous attempts (e.g. by request signers or other interceptors).
     *
     * **Error Behavior**: Errors raised by this hook will be stored until all interceptors have had their
     * `readBeforeAttempt` hook invoked. Other hooks will then be skipped and execution will jump to
     * [modifyBeforeAttemptCompletion] with the raised error as the [ResponseInterceptorContext.response] result.
     * If multiple interceptors raise an error in `readBeforeAttempt` then the latest will be used and earlier
     * ones will be logged and added as suppressed exceptions.
     */
    public fun readBeforeAttempt(context: ProtocolRequestInterceptorContext<I, TReq>): Unit {}


    /**
     * A hook called before the transport request message is signed. This method has the ability to modify and
     * return a new transport request.
     *
     * **When**: This will ALWAYS be called once per _attempt_, except when a failure occurs earlier in the
     * request pipeline. This method may be called multiple times in the event of retries.
     *
     * **Available Information**: [RequestInterceptorContext.request] and
     * [ProtocolRequestInterceptorContext.transmitRequest] are ALWAYS available. The
     * [ProtocolRequestInterceptorContext.transmitRequest] may have been modified by earlier `modifyBeforeSigning` hooks
     * and may be modified further by later hooks. In the event of retries, the context will not include changes made
     * in previous attempts (e.g. by request signers or other interceptors).
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [modifyBeforeAttemptCompletion]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun modifyBeforeSigning(context: ProtocolRequestInterceptorContext<I, TReq>): TReq =
        checkNotNull(context.transmitRequest) { "modifyBeforeSigning: transmitRequest must not be null" }

    /**
     * A hook called before the transport request message is signed.
     *
     * **When**: This will ALWAYS be called once per _attempt_, except when a failure occurs earlier in the
     * request pipeline. This method may be called multiple times in the event of retries. The duration between
     * invocation of this hook and [readAfterSigning] is very close to the amount of time spent signing the request.
     *
     * **Available Information**: [RequestInterceptorContext.request] and
     * [ProtocolRequestInterceptorContext.transmitRequest] are ALWAYS available. In the event of retries, the context
     * will not include changes made in previous attempts (e.g. by request signers or other interceptors).
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [modifyBeforeAttemptCompletion]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun readBeforeSigning(context: ProtocolRequestInterceptorContext<I, TReq>): Unit {}

    /**
     * A hook called after the transport request message is signed.
     *
     * **When**: This will ALWAYS be called once per _attempt_, except when a failure occurs earlier in the
     * request pipeline. This method may be called multiple times in the event of retries. The duration between
     * invocation of this hook and [readBeforeSigning] is very close to the amount of time spent signing the request.
     *
     * **Available Information**: [RequestInterceptorContext.request] and
     * [ProtocolRequestInterceptorContext.transmitRequest] are ALWAYS available. In the event of retries, the context
     * will not include changes made in previous attempts (e.g. by request signers or other interceptors).
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [modifyBeforeAttemptCompletion]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun readAfterSigning(context: ProtocolRequestInterceptorContext<I, TReq>): Unit {}

    /**
     * A hook called before the transport request message is sent to the service. This method has the ability to modify
     * and return a new transport request.
     *
     * **When**: This will ALWAYS be called once per _attempt_, except when a failure occurs earlier in the
     * request pipeline. This method may be called multiple times in the event of retries.
     *
     * **Available Information**: [RequestInterceptorContext.request] and
     * [ProtocolRequestInterceptorContext.transmitRequest] are ALWAYS available. The
     * [ProtocolRequestInterceptorContext.transmitRequest] may have been modified by earlier `modifyBeforeSigning` hooks
     * and may be modified further by later hooks. In the event of retries, the context will not include changes made
     * in previous attempts (e.g. by request signers or other interceptors).
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [modifyBeforeAttemptCompletion]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun modifyBeforeTransmit(context: ProtocolRequestInterceptorContext<I, TReq>): TReq =
        checkNotNull(context.transmitRequest) { "modifyBeforeTransmit: transmitRequest must not be null" }

    /**
     * A hook called before the transport request message is sent to the service.
     *
     * **When**: This will ALWAYS be called once per _attempt_, except when a failure occurs earlier in the
     * request pipeline. This method may be called multiple times in the event of retries. The duration between
     * invocation of this hook and [readAfterTransmit] is very close to the amount of time it took to send
     * a request and receive a response from the service.
     *
     * **Available Information**: [RequestInterceptorContext.request] and
     * [ProtocolRequestInterceptorContext.transmitRequest] are ALWAYS available. In the event of retries, the context
     * will not include changes made in previous attempts (e.g. by request signers or other interceptors).
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [modifyBeforeAttemptCompletion]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun readBeforeTransmit(context: ProtocolRequestInterceptorContext<I, TReq>): Unit {}

    /**
     * A hook called after the transport response message is received from the service.
     *
     * **When**: This will ALWAYS be called once per _attempt_, except when a failure occurs earlier in the
     * request pipeline. This method may be called multiple times in the event of retries. The duration between
     * invocation of this hook and [readBeforeTransmit] is very close to the amount of time it took to send
     * a request and receive a response from the service.
     *
     * **Available Information**: [RequestInterceptorContext.request],
     * [ProtocolRequestInterceptorContext.transmitRequest], and [ProtocolResponseInterceptorContext.transmitResponse]
     * are ALWAYS available. In the event of retries, the context will not include changes made in previous attempts
     * (e.g. by request signers or other interceptors).
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [modifyBeforeAttemptCompletion]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun readAfterTransmit(context: ProtocolResponseInterceptorContext<I, TReq, TResp>): Unit {}


    /**
     * A hook called before the transport request message is deserialized into the output response type.
     * This method has the ability to modify and return a new transport response.
     *
     * **When**: This will ALWAYS be called once per _attempt_, except when a failure occurs earlier in the
     * request pipeline. This method may be called multiple times in the event of retries.
     *
     * **Available Information**: [RequestInterceptorContext.request],
     * [ProtocolRequestInterceptorContext.transmitRequest], and [ProtocolResponseInterceptorContext.transmitResponse]
     * are ALWAYS available. The [ProtocolResponseInterceptorContext.transmitResponse] may have been modified by earlier
     * `modifyBeforeDeserialization` hooks and may be modified further by later hooks. In the event of retries, the
     * context will not include changes made in previous attempts (e.g. by request signers or other interceptors).
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [modifyBeforeAttemptCompletion]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun modifyBeforeDeserialization(context: ProtocolResponseInterceptorContext<I, TReq, TResp>): TResp =
        checkNotNull(context.transmitResponse) { "modifyBeforeDeserialization: transmitResponse must not be null" }

    /**
     * A hook called before the transport request message is deserialized into the output response type.
     *
     * **When**: This will ALWAYS be called once per _attempt_, except when a failure occurs earlier in the
     * request pipeline. This method may be called multiple times in the event of retries. The duration between
     * invocation of this hook and [readAfterDeserialization] is very close to the amount of time spent deserializing
     * the protocol response into the modeled operation response.
     *
     * **Available Information**: [RequestInterceptorContext.request],
     * [ProtocolRequestInterceptorContext.transmitRequest], and [ProtocolResponseInterceptorContext.transmitResponse]
     * are ALWAYS available. In the event of retries, the context will not include changes made in previous attempts
     * (e.g. by request signers or other interceptors).
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [modifyBeforeAttemptCompletion]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun readBeforeDeserialization(context: ProtocolResponseInterceptorContext<I, TReq, TResp>): Unit {}

    /**
     * A hook called after the transport request message is deserialized into the output response type.
     *
     * **When**: This will ALWAYS be called once per _attempt_, except when a failure occurs earlier in the
     * request pipeline. This method may be called multiple times in the event of retries. The duration between
     * invocation of this hook and [readBeforeDeserialization] is very close to the amount of time spent deserializing
     * the protocol response into the modeled operation response.
     *
     * **Available Information**: [RequestInterceptorContext.request],
     * [ProtocolRequestInterceptorContext.transmitRequest], [ProtocolResponseInterceptorContext.transmitResponse],
     * and [ResponseInterceptorContext.response] are ALWAYS available. In the event of retries, the context will not
     * include changes made in previous attempts (e.g. by request signers or other interceptors).
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [modifyBeforeAttemptCompletion]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun readAfterDeserialization(context: ResponseInterceptorContext<I, O, TReq, TResp>): Unit {}

    /**
     * A hook called when an attempt is completed. This method has the ability to modify and return a new operation
     * output or error.
     *
     * **When**: This will ALWAYS be called once per _attempt_, except when a failure occurs before [readBeforeAttempt]
     * This method may be called multiple times in the event of retries.
     *
     * **Available Information**: [RequestInterceptorContext.request],
     * [ProtocolRequestInterceptorContext.transmitRequest], [ProtocolResponseInterceptorContext.transmitResponse],
     * and [ResponseInterceptorContext.response] are ALWAYS available. In the event of retries, the context will not
     * include changes made in previous attempts (e.g. by request signers or other interceptors).
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [readAfterAttempt]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun modifyBeforeAttemptCompletion(context: ResponseInterceptorContext<I, O, TReq, TResp>): Result<O> =
        context.response

    /**
     * A hook called when an attempt is completed.
     *
     * **When**: This will ALWAYS be called once per _attempt_, as long as [readBeforeAttempt] was executed.
     *
     * **Available Information**: [RequestInterceptorContext.request],
     * [ProtocolRequestInterceptorContext.transmitRequest], and [ResponseInterceptorContext.response] are ALWAYS
     * available.[ProtocolResponseInterceptorContext.transmitResponse] is available if a response was received
     * by the service for this attempt. In the event of retries, the context will not include changes made in previous
     * attempts (e.g. by request signers or other interceptors).
     *
     * **Error Behavior**: Errors raised by this hook will be stored until all interceptors have had their
     * `readAfterAttempt` hook invoked. If multiple interceptors raise an error in `readAfterAttempt` then the latest
     * will be used and earlier ones will be logged and added as suppressed exceptions. If the result of the attempt
     * is determined to be retryable then execution will jump to [readBeforeAttempt]. Otherwise, execution will jump
     * to [modifyBeforeCompletion] with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun readAfterAttempt(context: ResponseInterceptorContext<I, O, TReq, TResp>): Unit {}

    /**
     * A hook called when an attempt is completed. This method has the ability to modify and return a new operation
     * output or error.
     *
     * **When**: This will ALWAYS be called once per execution.
     *
     * **Available Information**: [RequestInterceptorContext.request] and [ResponseInterceptorContext.response]
     * are ALWAYS available. [ProtocolRequestInterceptorContext.transmitRequest] and
     * [ProtocolResponseInterceptorContext.transmitResponse] are available if the execution proceeded far enough for
     * them to be generated.
     *
     * **Error Behavior**: If errors are raised by this hook, execution will jump to [readAfterExecution]
     * with the raised error as the [ResponseInterceptorContext.response] result.
     */
    public fun modifyBeforeCompletion(context: ResponseInterceptorContext<I, O, TReq, TResp>): Result<O> =
        context.response

    /**
     * A hook called when an attempt is completed.
     *
     * **When**: This will ALWAYS be called once per execution. The duration between invocation of this hook
     * and [readBeforeExecution] is very close to the full duration of the execution.
     *
     * **Available Information**: [RequestInterceptorContext.request] and [ResponseInterceptorContext.response]
     * are ALWAYS available. [ProtocolRequestInterceptorContext.transmitRequest] and
     * [ProtocolResponseInterceptorContext.transmitResponse] are available if the execution proceeded far enough for
     * them to be generated.
     *
     * **Error Behavior**: Errors raised by this hook will be stored until all interceptors have had their
     * `readAfterExecution` hook invoked. If multiple interceptors raise an error in `readAfterExecution` then the
     * latest will be used and earlier ones will be logged and added as suppressed exceptions. The error will then
     * be treated as the final response to the caller.
     */
    public fun readAfterExecution(context: ResponseInterceptorContext<I, O, TReq, TResp>): Unit {}

}

/**
 * [Interceptor] context used for all phases that only have access to the operation input (request)
 */
public interface RequestInterceptorContext<I>: Attributes {

    /**
     * Retrieve the modeled request for the operation being invoked
     */
    public val request: I
}


/**
 * [Interceptor] context used for all phases that have access to the operation input (request) and the
 * serialized protocol specific request (e.g. HttpRequest).
 */
public interface ProtocolRequestInterceptorContext<I, TReq>: RequestInterceptorContext<I> {
    /**
     * Retrieve the transmittable (protocol specific) request for the operation being invoked.
     */
    public val transmitRequest: TReq?
}

/**
 * [Interceptor] context used for all phases that have access to the operation input (request), the
 * serialized protocol specific request (e.g. HttpRequest), and the protocol specific response (e.g. HttpResponse).
 */
public interface ProtocolResponseInterceptorContext<I, TReq, TResp>:
    ProtocolRequestInterceptorContext<I, TReq>
{
    /**
     * Retrieve the transmittable (protocol specific) response for the operation being invoked.
     */
    public val transmitResponse: TResp?
}

/**
 * [Interceptor] context used for all phases that have access to the operation input (request), the
 * serialized protocol specific request (e.g. HttpRequest), the protocol specific response (e.g. HttpResponse),m
 * and the deserialized operation output (response).
 */
public interface ResponseInterceptorContext<I, O, TReq, TResp>: ProtocolResponseInterceptorContext<I, TReq, TResp> {

    /**
     * Retrieve the modeled response or exception for the operation being invoked
     */
    public val response: Result<O>
}

// public interface InterceptorContext<I, O, TReq, TResp>:
//     ReadRequestInterceptorContext<I>,
//     ReadProtocolRequestInterceptorContext<I, TReq>,
//     ReadProtocolResponseInterceptorContext<I, TReq, TResp>,
//     ReadResponseInterceptorContext<I, O>



private fun <I> testReadReq(ctx: RequestInterceptorContext<I>) {

}

private fun <I, TReq> testReadTransmitReq(ctx: ProtocolRequestInterceptorContext<I, TReq>) {

}

private fun <I, TReq, TResp> testReadTransmitResp(ctx: ProtocolResponseInterceptorContext<I, TReq, TResp>) {

}

private fun <I, O, TReq, TResp> testReadResp(ctx: ResponseInterceptorContext<I, O, TReq, TResp>) {

}

private fun <TReq, TResp> takeInterceptor(interceptor: Interceptor<Any, Any, TReq, TResp>) {

}

private fun <TReq , TResp > testTakeInterceptor(interceptor: Interceptor<String, Int, TReq, TResp>) {
    takeInterceptor(interceptor as Interceptor<Any, Any, TReq, TResp>)
    val x = interceptor as Interceptor<Any, Any, TReq, TResp>
}
