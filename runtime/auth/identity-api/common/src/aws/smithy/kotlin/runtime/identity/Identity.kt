/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.identity

import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.time.Instant

/**
 * Uniquely-distinguishing properties which identify an actor
 */
public interface Identity {
    /**
     * The time after which this identity will no longer be valid. If this is null, an expiration time
     * is not known (but the identity may still expire at some point in the future).
     */
    public val expiration: Instant?

    /**
     * Typed property bag of attributes associated with this identity. Common attribute keys are defined in
     * [IdentityAttributes].
     */
    public val attributes: Attributes
}
