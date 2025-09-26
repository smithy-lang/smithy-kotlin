/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.auth.awssigning.crt.CrtAwsSigner

class CrtAwsHttpSignerTest : AwsHttpSignerTestBase(CrtAwsSigner)
