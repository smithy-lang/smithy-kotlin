/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.auth.awssigning

/**
 * Specifies a hash for a signable request
 */
sealed class HashSpecification {
    /**
     * Indicates that the hash value should be calculated from the body payload of the request
     */
    object CalculateFromPayload : HashSpecification()

    /**
     * Specifies a literal value to use as a hash
     */
    sealed class HashLiteral(open val hash: String) : HashSpecification()

    /**
     * The hash value should indicate an unsigned payload
     */
    object UnsignedPayload : HashLiteral("UNSIGNED-PAYLOAD")

    /**
     * The hash value should indicate an empty body
     */
    object EmptyBody : HashLiteral("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855") // hash of ""

    /**
     * The hash value should indicate that signature covers only headers and that there is no payload
     */
    object StreamingAws4HmacSha256Payload : HashLiteral("STREAMING-AWS4-HMAC-SHA256-PAYLOAD")

    /**
     * The hash value should indicate ???
     */
    object StreamingAws4HmacSha256Events : HashLiteral("STREAMING-AWS4-HMAC-SHA256-EVENTS")

    /**
     * Use an explicit, precalculated value for the hash
     */
    data class Precalculated(override val hash: String) : HashLiteral(hash)
}
