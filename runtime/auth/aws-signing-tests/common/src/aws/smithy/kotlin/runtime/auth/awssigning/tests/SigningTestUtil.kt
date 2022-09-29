/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awssigning.tests

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.tracing.TraceSpan

public val testCredentialsProvider: CredentialsProvider = Credentials("AKID", "SECRET", "SESSION").asStaticProvider()

public fun Credentials.asStaticProvider(): CredentialsProvider = object : CredentialsProvider {
    override suspend fun getCredentials(traceSpan: TraceSpan): Credentials = this@asStaticProvider
}
