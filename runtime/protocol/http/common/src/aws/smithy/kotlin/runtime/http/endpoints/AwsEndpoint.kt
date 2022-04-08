/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http.endpoints

import aws.smithy.kotlin.runtime.http.Url

/**
 * Represents the endpoint a service client should make API operation calls to.
 *
 * The SDK will automatically resolve these endpoints per API client using an internal resolver.
 *
 * @property endpoint The endpoint clients will use to make API calls
 * to e.g. "{service-id}.{region}.amazonaws.com"
 * @property credentialScope Custom signing constraint overrides
 */
data class AwsEndpoint(val endpoint: Endpoint, val credentialScope: CredentialScope? = null) {
    constructor(url: Url, credentialScope: CredentialScope? = null) : this(Endpoint(url), credentialScope)
    constructor(url: String, credentialScope: CredentialScope? = null) : this(Endpoint(Url.parse(url)), credentialScope)
}
