/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.auth.awssigning.crt

import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigner
import aws.smithy.kotlin.runtime.auth.awssigning.tests.SigningSuiteTestBase

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

        // FIXME - Signature mismatch possibly related to https://github.com/awslabs/aws-crt-java/pull/419. Needs
        // investigation.
        "get-utf8",
    )
}
