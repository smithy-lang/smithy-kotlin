/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awscredentials

import aws.smithy.kotlin.runtime.identity.Identity
import aws.smithy.kotlin.runtime.identity.IdentityAttributes
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.util.*

/**
 * Represents a set of AWS credentials
 *
 * For more information see [AWS security credentials](https://docs.aws.amazon.com/general/latest/gr/aws-security-credentials.html#AccessKeys)
 */
public interface Credentials : Identity {
    public companion object {
        /**
         * Create a new [Credentials] instance
         */
        public operator fun invoke(
            accessKeyId: String,
            secretAccessKey: String,
            sessionToken: String? = null,
            expiration: Instant? = null,
            providerName: String? = null,
            attributes: Attributes? = null,
        ): Credentials {
            val resolvedAttributes = if (providerName != null && attributes?.getOrNull(IdentityAttributes.ProviderName) != providerName) {
                val merged = attributes?.toMutableAttributes() ?: mutableAttributes()
                merged.setIfValueNotNull(IdentityAttributes.ProviderName, providerName)
                merged
            } else {
                attributes ?: emptyAttributes()
            }

            return CredentialsImpl(accessKeyId, secretAccessKey, sessionToken, expiration, resolvedAttributes)
        }
    }

    /**
     * Identifies the user interacting with services
     */
    public val accessKeyId: String

    /**
     * Secret key used to authenticate the user and sign requests
     */
    public val secretAccessKey: String

    /**
     * Session token associated with short term credentials with an expiration.
     */
    public val sessionToken: String?
        get() = null

    /**
     * The name of the credentials provider that sourced these credentials (if known).
     */
    public val providerName: String?
        get() = attributes.getOrNull(IdentityAttributes.ProviderName)
}

public fun Credentials.copy(
    accessKeyId: String = this.accessKeyId,
    secretAccessKey: String = this.secretAccessKey,
    sessionToken: String? = this.sessionToken,
    expiration: Instant? = this.expiration,
    providerName: String? = this.providerName,
    attributes: Attributes? = this.attributes,
): Credentials {
    val updatedAttributes = if (this.providerName != null && providerName == null) {
        // if provider name was previously set and is updated to null we need to remove it from the current attributes
        attributes?.toMutableAttributes()?.apply {
            remove(IdentityAttributes.ProviderName)
        }?.takeIf(Attributes::isNotEmpty)
    } else {
        attributes
    }
    return Credentials(accessKeyId, secretAccessKey, sessionToken, expiration, providerName, updatedAttributes)
}

private data class CredentialsImpl(
    override val accessKeyId: String,
    override val secretAccessKey: String,
    override val sessionToken: String? = null,
    override val expiration: Instant? = null,
    override val attributes: Attributes = emptyAttributes(),
) : Credentials
