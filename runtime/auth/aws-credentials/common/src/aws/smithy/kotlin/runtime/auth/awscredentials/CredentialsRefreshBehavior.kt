/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awscredentials

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.collections.AttributeKey

/**
 * Declares how a resolved set of [Credentials] should be treated by a refresh-resilient cache. A provider stamps this
 * on the credentials it produces; the cache reads it (see [policyFor]) to decide which refresh policy applies.
 *
 * This is a typed opt-in. Credentials that carry no declaration are cached but never granted static stability, so a
 * custom provider cannot inherit an AWS-managed provider's behavior by accident — including by reporting a
 * `providerName` that collides with a built-in provider's.
 */
@InternalApi
public enum class CredentialsRefreshBehavior {
    /** A refreshable AWS-managed source whose credentials may be served past expiration when a refresh fails. */
    RefreshableWithStaticStability,

    /** A static source with nothing to refresh. */
    NonRefreshable,
}

/** The [AttributeKey] under which a provider declares its [CredentialsRefreshBehavior]. */
@InternalApi
public val CredentialsRefreshBehaviorKey: AttributeKey<CredentialsRefreshBehavior> =
    AttributeKey("aws.smithy.kotlin#CredentialsRefreshBehavior")

/** The [CredentialsRefreshBehavior] this value declares, or null if it declares none. */
@InternalApi
public val Credentials.refreshBehavior: CredentialsRefreshBehavior?
    get() = attributes.getOrNull(CredentialsRefreshBehaviorKey)
