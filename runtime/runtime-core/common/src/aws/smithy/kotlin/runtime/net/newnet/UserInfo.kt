/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net.newnet

import aws.smithy.kotlin.runtime.util.text.encoding.Encodable
import aws.smithy.kotlin.runtime.util.text.encoding.Encoding

/**
 * Represents the user authentication information in a URL
 * @param userName The user name of the caller
 * @param password The password for the caller
 */
public class UserInfo private constructor(public val userName: Encodable, public val password: Encodable) {
    public companion object {
        /**
         * Create a new [UserInfo] via a DSL builder block
         * @param block The code to apply to the builder
         * @return A new [UserInfo] instance
         */
        public operator fun invoke(block: Builder.() -> Unit): UserInfo = Builder().apply(block).build()

        /**
         * Parse a **decoded** string into a [UserInfo] instance
         * @param decoded A decoded user info string
         * @return A new [UserInfo] instance
         */
        public fun parseDecoded(decoded: String): UserInfo = UserInfo { parseDecoded(decoded) }

        /**
         * Parse an **encoded** string into a [UserInfo] instance
         * @param encoded An encoded user info string
         * @return A new [UserInfo] instance
         */
        public fun parseEncoded(encoded: String): UserInfo = UserInfo { parseEncoded(encoded) }
    }

    /**
     * Copy the properties of this [UserInfo] instance into a new [Builder] object. Any changes to the builder
     * *will not* affect this instance.
     */
    public fun toBuilder(): Builder = Builder(this)

    override fun toString(): String = "${userName.encoded}:${password.encoded}"

    /**
     * A mutable builder used to construct [UserInfo] instances
     */
    public class Builder internal constructor(userInfo: UserInfo?) {
        /**
         * Initialize an empty [UserInfo] builder
         */
        public constructor() : this(null)

        private var userName = userInfo?.userName ?: Encodable.Empty

        public var userNameDecoded: String
            get() = userName.decoded
            set(value) { userName = Encoding.UserInfo.encodableFromDecoded(value) }

        public var userNameEncoded: String
            get() = userName.encoded
            set(value) { userName = Encoding.UserInfo.encodableFromEncoded(value) }

        private var password = userInfo?.password ?: Encodable.Empty

        public var passwordDecoded: String
            get() = password.decoded
            set(value) { password = Encoding.UserInfo.encodableFromDecoded(value) }

        public var passwordEncoded: String
            get() = password.encoded
            set(value) { password = Encoding.UserInfo.encodableFromEncoded(value) }

        internal fun parseDecoded(decoded: String) = parse(decoded, Encoding.UserInfo::encodableFromDecoded)
        internal fun parseEncoded(encoded: String) = parse(encoded, Encoding.UserInfo::encodableFromEncoded)

        private fun parse(text: String, toEncodable: (String) -> Encodable) {
            if (text.isEmpty()) {
                userName = Encodable.Empty
                password = Encodable.Empty
            } else {
                val parts = text.split(":", limit = 2)
                userName = toEncodable(parts[0])
                password = when (parts.size) {
                    1 -> Encodable.Empty
                    2 -> toEncodable(parts[1])
                    else -> throw IllegalArgumentException("invalid user info string $text")
                }
            }
        }

        /**
         * Build a new [UserInfo] from the currently-configured builder values
         * @return A new [UserInfo] instance
         */
        public fun build(): UserInfo = UserInfo(userName, password)
    }
}
