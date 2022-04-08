/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.auth.signing.awssigning.tests

import aws.smithy.kotlin.runtime.auth.signing.awssigning.common.AwsSigner

interface HasSigner {
    val signer: AwsSigner
}
