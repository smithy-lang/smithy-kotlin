// ktlint-disable filename
/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.util

/**
 * API marked with this annotation is internal to the client runtime and it is not intended to be used outside.
 * It could be modified or removed without any notice. Using it outside of the client-runtime could cause undefined behaviour and/or
 * any strange effects.
 *
 * We strongly recommend to not use such API.
 */
@Suppress("DEPRECATION")
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This API is internal to smithy-client-rt and should not be used. It could be removed or changed without notice."
)
@Experimental(level = Experimental.Level.ERROR)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD,
    AnnotationTarget.CONSTRUCTOR
)
annotation class InternalAPI
