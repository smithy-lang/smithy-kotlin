/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.auth.signing.awssigning.crt

import aws.smithy.kotlin.runtime.auth.signing.awssigning.common.AwsSigner
import aws.smithy.kotlin.runtime.auth.signing.awssigning.tests.SigningSuiteTestBase

class CrtSigningSuiteTest : SigningSuiteTestBase() {
    override val signer: AwsSigner = CrtAwsSigner

    override val disabledTests = setOf(
        // ktor-http-cio parser doesn't support parsing multiline headers since they are deprecated in RFC7230
        "get-header-value-multiline",
        // ktor fails to parse with space in it (expects it to be a valid request already encoded)
        "get-space-normalized",
        "get-space-unnormalized",

        // no signed request to test against
        "get-vanilla-query-order-key",
        "get-vanilla-query-order-value",

        // FIXME - crt-java has utf8 bug when converting request,
        // re-enable after https://github.com/awslabs/aws-crt-java/pull/419 is merged
        "get-vanilla-utf8-query",
        "get-utf8",
    )
}
