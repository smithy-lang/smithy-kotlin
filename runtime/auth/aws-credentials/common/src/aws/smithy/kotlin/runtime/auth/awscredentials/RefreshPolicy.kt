/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awscredentials

import aws.smithy.kotlin.runtime.InternalApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The refresh behaviors that apply to one resolved set of [Credentials], selected by the provider that produced it.
 *
 * Note what is deliberately *not* here: error classification. A resolution *failure* produces no credentials and so
 * carries no declared behavior to dispatch on, and the previously cached value's policy cannot stand in for it: the
 * failure may have come from a different provider in the chain. See [isNonRecoverableCredentialsError].
 *
 * @param staticStability whether cached credentials may be used past their expiration when a refresh fails. This
 * is granted only to providers whose credential source is an AWS-managed service.
 * @param advisoryWindowFor computes the advisory refresh window from the credential lifetime
 * @param undatedCadence how often to re-resolve a value that carries no expiration. Null takes the cache's default
 * lifetime. A cadence may only move the re-read earlier than the default lifetime, never later.
 * @param pacedRefresh whether the re-read is held to that pace even when the value carries an expiration. True for
 * the sources this feature does not govern, so their timing stays what it is today; false for the ones it does, whose
 * re-read point follows the expiration through the window table.
 */
@InternalApi
public class RefreshPolicy internal constructor(
    internal val staticStability: Boolean,
    internal val advisoryWindowFor: (Duration) -> Duration,
    internal val undatedCadence: Duration? = null,
    internal val pacedRefresh: Boolean = false,
) {
    internal fun copy(
        staticStability: Boolean = this.staticStability,
        advisoryWindowFor: (Duration) -> Duration = this.advisoryWindowFor,
        undatedCadence: Duration? = this.undatedCadence,
        pacedRefresh: Boolean = this.pacedRefresh,
    ): RefreshPolicy = RefreshPolicy(staticStability, advisoryWindowFor, undatedCadence, pacedRefresh)

    @InternalApi
    public companion object {
        /** In scope for the full refresh lifecycle: caching, both windows, static stability, backoff. */
        internal val FullLifecycle: RefreshPolicy = RefreshPolicy(
            staticStability = true,
            advisoryWindowFor = ::standardAdvisoryWindow,
        )

        /**
         * Cached on the standard refresh timing; static stability does not apply. The fall-through for a
         * value whose provider declared nothing, which includes process and customer-provided sources.
         */
        internal val CachingOnly: RefreshPolicy = RefreshPolicy(
            staticStability = false,
            advisoryWindowFor = ::standardAdvisoryWindow,
            pacedRefresh = true,
        )

        /**
         * Providers with no refreshable source (environment, JVM system properties, static). Identical to
         * [CachingOnly] except that the re-read is paced at a minute rather than at the default lifetime: the
         * read is one in-process lookup, and a system property can change under the SDK by System.setProperty.
         */
        internal val Passthrough: RefreshPolicy = CachingOnly.copy(undatedCadence = 1.minutes)
    }
}

/**
 * Selects the [RefreshPolicy] for a resolved credential from the [CredentialsRefreshBehavior] its provider declared.
 *
 * A value that declares nothing yields [RefreshPolicy.CachingOnly]: process and custom providers must not be
 * granted static stability by accident, so the fall-through is to withhold it unless a provider opts in
 * explicitly.
 */
internal fun policyFor(behavior: CredentialsRefreshBehavior?): RefreshPolicy = when (behavior) {
    CredentialsRefreshBehavior.RefreshableWithStaticStability -> RefreshPolicy.FullLifecycle
    CredentialsRefreshBehavior.NonRefreshable -> RefreshPolicy.Passthrough
    null -> RefreshPolicy.CachingOnly
}

/**
 * The standard advisory window table.
 *
 * The table as specified, with no clamp and no exception on top of it: a lifetime shorter than the table
 * value means every resolution attempts a refresh until one fails and installs the backoff, which is
 * accepted rather than patched.
 */
internal fun standardAdvisoryWindow(lifetime: Duration): Duration = when {
    lifetime <= 20.minutes -> 5.minutes
    lifetime < 90.minutes -> 15.minutes
    else -> 60.minutes
}
