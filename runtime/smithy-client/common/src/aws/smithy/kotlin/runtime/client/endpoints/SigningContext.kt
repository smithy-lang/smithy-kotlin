/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.client.endpoints

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.auth.AuthOption
import aws.smithy.kotlin.runtime.collections.AttributeKey

/**
 * Static attribute key for AWS endpoint auth schemes that can influence the signing context
 */
@InternalApi
public val SigningContextAttributeKey: AttributeKey<List<AuthOption>> = AttributeKey("aws.smithy.kotlin#endpointAuthSchemes")

/**
 * Sugar extension to pull the auth option(s) out of the endpoint attributes.
 */
@InternalApi
public val Endpoint.authOptions: List<AuthOption>
    get() = attributes.getOrNull(SigningContextAttributeKey) ?: emptyList()
