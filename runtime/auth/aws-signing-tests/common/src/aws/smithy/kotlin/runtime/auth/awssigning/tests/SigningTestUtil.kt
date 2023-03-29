/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awssigning.tests

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.util.Attributes

public val DEFAULT_TEST_CREDENTIALS: Credentials = Credentials("AKID", "SECRET", "SESSION")
public val DEFAULT_TEST_CREDENTIALS_PROVIDER: CredentialsProvider = DEFAULT_TEST_CREDENTIALS.asStaticProvider()

public fun Credentials.asStaticProvider(): CredentialsProvider = object : CredentialsProvider {
    override suspend fun resolve(attributes: Attributes): Credentials = this@asStaticProvider
}
