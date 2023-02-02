/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime

/**
 * API marked with this annotation is internal to the client runtime, and it is not intended to be used outside.
 * It could be modified or removed without any notice. Using it outside the client-runtime could cause undefined behaviour and/or
 * any strange effects.
 *
 * We strongly recommend to not use such API.
 */
@Suppress("DEPRECATION")
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
