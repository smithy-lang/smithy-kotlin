/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.auth.signing.awssigning.tests

import aws.smithy.kotlin.runtime.auth.credentials.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.credentials.awscredentials.CredentialsProvider

val testCredentialsProvider = StaticCredentialsProvider(Credentials("AKID", "SECRET", "SESSION"))

class StaticCredentialsProvider(private val credentials: Credentials) : CredentialsProvider {
    override suspend fun getCredentials(): Credentials = credentials
}
