/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.endpoints

import aws.smithy.kotlin.runtime.http.Url
import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * Represents the endpoint a service client should make API operation calls to.
 *
 * The SDK will automatically resolve these endpoints per API client using an internal resolver.
 *
 * If the hostname is mutable the SDK clients may modify any part of the hostname based
 * on the requirements of the API (e.g. adding or removing content in the hostname).
 *
 * As an example Amazon S3 Client prefixing "bucketname" to the hostname or changing th hostname
 * service name component from "s3" to "s3-accespoint.dualstack." requires mutable hostnames.
 *
 * Care should be taken when setting this flag and providing a custom endpoint. If the hostname
 * is expected to be mutable and the client cannot modify the endpoint correctly, the operation
 * will likely fail.
 */
public class Endpoint {
    /**
     * The base URL endpoint clients will use to make API calls to e.g. "api.myservice.com".
     * NOTE: Only `scheme`, `port`, `host` `path`, and `parameters` are valid. Other URL elements are ignored.
     */
    public val uri: Url

    /**
     * Flag indicating whether the hostname can be modified by the SDK client.
     */
    public val isHostnameImmutable: Boolean

    /**
     * A map of additional HTTP headers to be set when making calls against this endpoint.
     */
    public val headers: Map<String, List<String>>

    /**
     * A grab-bag property map of endpoint attributes. The values here are only set when the endpoint returned from
     * evaluating a ruleset.
     */
    @InternalApi
    public val attributes: Map<String, Any>

    public constructor(
        uri: Url,
        isHostnameImmutable: Boolean = false,
        headers: Map<String, List<String>> = mapOf(),
    ) {
        this.uri = uri
        this.isHostnameImmutable = isHostnameImmutable
        this.headers = headers
        this.attributes = mapOf()
    }

    public constructor(uri: String) : this(Url.parse(uri))
    @InternalApi
    public constructor(
        uri: Url,
        isHostnameImmutable: Boolean = false,
        headers: Map<String, List<String>> = mapOf(),
        attributes: Map<String, Any> = mapOf(),
    ) {
        this.uri = uri
        this.isHostnameImmutable = isHostnameImmutable
        this.headers = headers
        this.attributes = attributes
    }
}
