/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.identity

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.io.Closeable
import aws.smithy.kotlin.runtime.telemetry.logging.logger
import aws.smithy.kotlin.runtime.telemetry.trace.withSpan

// TODO - support caching the provider that actually resolved credentials such that future calls don't involve going through the full chain

/**
 * Composite [IdentityProvider] that delegates to a chain of providers. When asked for identity, [providers]
 * are consulted in the order given until one succeeds. If none of the providers in the chain can provide an identity
 * then this class will throw an exception. The exception will include the providers tried in the message. Each
 * individual exception is available as a suppressed exception.
 *
 * @param providers the list of providers to delegate to
 */
@InternalApi
public abstract class IdentityProviderChain<P : IdentityProvider, I : Identity> (
    protected vararg val providers: P,
) : CloseableIdentityProvider {
    init {
        require(providers.isNotEmpty()) { "at least one provider must be in the chain" }
    }
    override fun toString(): String =
        (listOf(this) + providers).map { it::class.simpleName }.joinToString(" -> ")

    override suspend fun resolve(attributes: Attributes): I = withSpan<IdentityProviderChain<*, *>, I>("ResolveIdentityChain") {
        val logger = coroutineContext.logger<IdentityProviderChain<*, *>>()
        val chain = this@IdentityProviderChain
        val chainException = lazy { IdentityProviderException("No identity could be resolved from the chain: $chain") }
        for (provider in providers) {
            logger.trace { "Attempting to resolve identity from $provider" }
            try {
                @Suppress("UNCHECKED_CAST")
                return@withSpan provider.resolve(attributes) as I
            } catch (ex: Exception) {
                logger.debug { "unable to resolve identity from $provider: ${ex.message}" }
                chainException.value.addSuppressed(ex)
            }
        }

        throw chainException.value
    }
    override fun close() {
        val exceptions = providers.mapNotNull {
            try {
                (it as? Closeable)?.close()
                null
            } catch (ex: Exception) {
                ex
            }
        }
        if (exceptions.isNotEmpty()) {
            val ex = exceptions.first()
            exceptions.drop(1).forEach(ex::addSuppressed)
            throw ex
        }
    }
}
