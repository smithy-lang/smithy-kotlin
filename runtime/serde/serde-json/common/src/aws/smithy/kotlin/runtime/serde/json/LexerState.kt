/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.serde.json

internal enum class LexerState {
    /**
     * Entry point. Expecting any JSON value
     */
    Initial,

    /**
     * Expecting the next token to be the *first* value in an array, or the end of the array.
     */
    ArrayFirstValueOrEnd,

    /**
     * Expecting the next token to the next value in an array, or the end of the array.
     */
    ArrayNextValueOrEnd,

    /**
     * Expecting the next token to be the *first* key in the object, or the end of the object.
     */
    ObjectFirstKeyOrEnd,

    /**
     * Expecting the next token to the next object key, or the end of the object.
     */
    ObjectNextKeyOrEnd,

    /**
     * Expecting the next token to be the value of a field in an object.
     */
    ObjectFieldValue,
}
