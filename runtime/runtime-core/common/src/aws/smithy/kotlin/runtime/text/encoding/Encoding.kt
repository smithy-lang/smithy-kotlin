/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.text.encoding

import aws.smithy.kotlin.runtime.InternalApi

@InternalApi
public interface Encoding {
    @InternalApi
    public companion object {
        internal val None = object : Encoding {
            override val name = "(no encoding)"
            override fun decode(encoded: String) = encoded
            override fun encode(decoded: String) = decoded
        }
    }

    public val name: String

    public fun decode(encoded: String): String
    public fun encode(decoded: String): String

    public fun encodableFromDecoded(decoded: String): Encodable = Encodable(decoded, encode(decoded), this)
    public fun encodableFromEncoded(encoded: String): Encodable {
        val decoded = decode(encoded)
        val reencoded = encode(decoded) // TODO is this right?
        return Encodable(decoded, reencoded, this)
    }
}
