/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.text.encoding

import aws.smithy.kotlin.runtime.InternalApi

/**
 * An algorithm which can convert data between a decoded string and an encoded string
 */
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

    /**
     * The name of this encoding
     */
    public val name: String

    /**
     * Given **encoded** data, returns the **decoded* representation
     */
    public fun decode(encoded: String): String

    /**
     * Given **decoded** data, returns the **encoded* representation
     */
    public fun encode(decoded: String): String

    /**
     * Given **decoded** data, returns an [Encodable] containing both the decoded and encoded data
     */
    public fun encodableFromDecoded(decoded: String): Encodable = Encodable(decoded, encode(decoded), this)

    /**
     * Given **encoded** data, returns an [Encodable] containing both the decoded and encoded data.
     */
    public fun encodableFromEncoded(encoded: String): Encodable = Encodable(decode(encoded), encoded, this)
}
