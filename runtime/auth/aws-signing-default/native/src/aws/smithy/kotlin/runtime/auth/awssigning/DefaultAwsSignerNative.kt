/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.auth.awssigning.crt.CrtAwsSigner

/** The default implementation of [AwsSigner] */
public actual val DefaultAwsSigner: AwsSigner = CrtAwsSigner
