/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.identity

/**
 * Composite [TokenProvider] that delegates to a chain of providers. When asked for identity, providers
 * are consulted in the order given until one succeeds. If none of the providers in the chain can provide an identity
 * then this class will throw an exception. The exception will include the providers tried in the message. Each
 * individual exception is available as a suppressed exception.
 *
 */
public typealias TokenProviderChain = IdentityProviderChain<TokenProvider, Token>
