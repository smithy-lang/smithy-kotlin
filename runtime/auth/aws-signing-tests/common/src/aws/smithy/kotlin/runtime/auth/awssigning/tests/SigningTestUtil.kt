/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awssigning.tests

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider

public val testCredentialsProvider: CredentialsProvider = Credentials("AKID", "SECRET", "SESSION").asStaticProvider()

public fun Credentials.asStaticProvider(): CredentialsProvider = object : CredentialsProvider {
    override suspend fun getCredentials(): Credentials = this@asStaticProvider

    override fun close() { }
}
