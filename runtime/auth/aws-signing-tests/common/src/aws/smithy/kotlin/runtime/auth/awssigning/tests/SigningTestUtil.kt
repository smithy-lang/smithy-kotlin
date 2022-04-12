/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.auth.awssigning.tests

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider

val testCredentialsProvider = Credentials("AKID", "SECRET", "SESSION").asStaticProvider()

fun Credentials.asStaticProvider() = object : CredentialsProvider {
    override suspend fun getCredentials(): Credentials = this@asStaticProvider
}
