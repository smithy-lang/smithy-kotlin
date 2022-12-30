/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

/**
 * Specifies a hash for a signable request
 */
public sealed class HashSpecification {
    /**
     * Indicates that the hash value should be calculated from the body payload of the request
     */
    public object CalculateFromPayload : HashSpecification()

    /**
     * Specifies a literal value to use as a hash
     */
    public sealed class HashLiteral(public open val hash: String) : HashSpecification()

    /**
     * The hash value should indicate an unsigned payload
     */
    public object UnsignedPayload : HashLiteral("UNSIGNED-PAYLOAD")

    /**
     * The hash value should indicate an empty body. The resulting hash literal is the SHA256 calculation for the empty
     * string.
     */
    public object EmptyBody : HashLiteral("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")

    /**
     * The hash value should indicate that signature covers only headers and that there is no payload
     */
    public object StreamingAws4HmacSha256Payload : HashLiteral("STREAMING-AWS4-HMAC-SHA256-PAYLOAD")

    /**
     * The hash value indicates that the streaming request will have trailers
     */
    public object StreamingAws4HmacSha256PayloadWithTrailers : HashLiteral("STREAMING-AWS4-HMAC-SHA256-PAYLOAD-TRAILER")

    /**
     * The hash value used for streaming unsigned requests with trailers
     */
    public object StreamingUnsignedPayloadWithTrailers : HashLiteral("STREAMING-UNSIGNED-PAYLOAD-TRAILER")

    /**
     * The hash value should indicate ???
     */
    public object StreamingAws4HmacSha256Events : HashLiteral("STREAMING-AWS4-HMAC-SHA256-EVENTS")

    /**
     * Use an explicit, precalculated value for the hash
     */
    public data class Precalculated(override val hash: String) : HashLiteral(hash)
}
