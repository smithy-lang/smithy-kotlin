/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awscredentials

import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.identity.IdentityProviderChain

/**
 * Composite [CredentialsProvider] that delegates to a chain of providers. When asked for credentials, providers
 * are consulted in the order given until one succeeds. If none of the providers in the chain can provide credentials
 * then this class will throw an exception. The exception will include the providers tried in the message. Each
 * individual exception is available as a suppressed exception.
 */
public class CredentialsProviderChain(vararg providers: CredentialsProvider) :
    IdentityProviderChain<CredentialsProvider, Credentials>(*providers), CredentialsProvider {

    public constructor(providers: List<CredentialsProvider>) : this(*providers.toTypedArray())

    override suspend fun resolve(attributes: Attributes): Credentials = super.resolve(attributes)
}
