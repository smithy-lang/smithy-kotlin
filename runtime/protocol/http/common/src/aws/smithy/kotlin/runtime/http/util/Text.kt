/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.util

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.text.VALID_PCHAR_DELIMS
import aws.smithy.kotlin.runtime.text.encodeUrlPath

// RFC-3986 §3.3 allows sub-delims (defined in section2.2) to be in the path component.
// This includes both colon ':' and comma ',' characters.
// Smithy protocol tests & AWS services percent encode these expected values. Signing
// will fail if these values are not percent encoded
private val VALID_HTTP_LABEL_DELIMS: Set<Char> = VALID_PCHAR_DELIMS - "/ :,?#[]()@!$&'*+;=%".toSet()

private val GREEDY_HTTP_LABEL_DELIMS: Set<Char> = VALID_HTTP_LABEL_DELIMS + '/'

/**
 * Encode a value that represents a member bound via `httpLabel`
 * See: https://awslabs.github.io/smithy/1.0/spec/core/http-traits.html#httplabel-trait
 *
 * @param greedy Flag indicating this label is "greedy" (which allows for the value to have path separators in it)
 * See: https://awslabs.github.io/smithy/1.0/spec/core/http-traits.html#greedy-labels
 */
@InternalApi
public fun String.encodeLabel(greedy: Boolean = false): String {
    val validDelims = if (greedy) {
        GREEDY_HTTP_LABEL_DELIMS
    } else {
        VALID_HTTP_LABEL_DELIMS
    }
    return encodeUrlPath(validDelims, checkPercentEncoded = false)
}
