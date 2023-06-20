/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime

/**
 * API marked with this annotation is internal to the runtime, and it is not intended to be used outside.
 * It could be modified or removed without any notice. Using it outside the runtime (or generated code) could cause
 * undefined behaviour and/or any strange effects.
 *
 * We strongly recommend to not use such API.
 *
 * Any usage of a declaration annotated with [InternalApi] must be accepted either by annotating that usage with
 * the OptIn annotation, e.g. `OptIn(InternalApi::class)`, or by using the compiler argument
 * `-opt-in=aws.smithy.kotlin.runtime.InternalApi`.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This API is internal to the smithy-kotlin runtime and should not be used. It could be removed or changed without notice.",
)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD,
    AnnotationTarget.CONSTRUCTOR,
)
public annotation class InternalApi

/**
 * API marked with this annotation is experimental and has a high chance of being changed or even removed.
 * Do not use it if you develop a library since your library will become binary incompatible with future
 * versions of the runtime.
 *
 * Any usage of a declaration annotated with [ExperimentalApi] must be accepted either by annotating that usage with
 * the OptIn annotation, e.g. `OptIn(ExperimentalApi::class)`, or by using the compiler argument
 * `-opt-in=aws.smithy.kotlin.runtime.ExperimentalApi`.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This API is experimental and has a high chance of being changed in the future. It could be removed or changed without notice.",
)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD,
    AnnotationTarget.CONSTRUCTOR,
)
public annotation class ExperimentalApi
