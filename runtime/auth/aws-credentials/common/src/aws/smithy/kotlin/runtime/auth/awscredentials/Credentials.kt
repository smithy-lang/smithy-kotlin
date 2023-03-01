/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awscredentials

import aws.smithy.kotlin.runtime.identity.Identity
import aws.smithy.kotlin.runtime.identity.IdentityAttributes
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.util.Attributes

/**
 * Represents a set of AWS credentials
 *
 * For more information see [AWS security credentials](https://docs.aws.amazon.com/general/latest/gr/aws-security-credentials.html#AccessKeys)
 */
public data class Credentials(
    val accessKeyId: String,
    val secretAccessKey: String,
    val sessionToken: String? = null,
    override val expiration: Instant? = null,
) : Identity {

    override val attributes: Attributes by lazy { Attributes() }
    public constructor(
        accessKeyId: String,
        secretAccessKey: String,
        sessionToken: String? = null,
        expiration: Instant? = null,
        providerName: String? = null,
    ) : this(accessKeyId, secretAccessKey, sessionToken, expiration) {
        providerName?.let {
            attributes[IdentityAttributes.ProviderName] = it
        }
    }
}
