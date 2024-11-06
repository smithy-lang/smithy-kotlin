/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awscredentials

import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.identity.IdentityProvider
import aws.smithy.kotlin.runtime.io.Closeable

/**
 * Represents a producer/source of AWS credentials
 */
public interface CredentialsProvider : IdentityProvider {
    /**
     * Request credentials from the provider
     */
    public override suspend fun resolve(attributes: Attributes): Credentials
}

/**
 * A [CredentialsProvider] with [Closeable] resources. Users SHOULD call [close] when done with the provider to ensure
 * any held resources are properly released.
 *
 * Implementations MUST evict any previously-retrieved or stored credentials when the provider is closed.
*/
public interface CloseableCredentialsProvider :
    CredentialsProvider,
    Closeable

/**
 * Retrieves the simple name of the class implementing [CredentialsProvider].
 *
 * This property uses Kotlin reflection to obtain the simple class name of
 * the current instance of [CredentialsProvider]. The simple class name is
 * the name of the class without the package qualification (e.g., "MyCredentialsProvider"
 * instead of "com.example.MyCredentialsProvider").
 */
public val CredentialsProvider.simpleClassName: String
    get() = this::class.simpleName ?: "AnonymousCredentialsProvider"
