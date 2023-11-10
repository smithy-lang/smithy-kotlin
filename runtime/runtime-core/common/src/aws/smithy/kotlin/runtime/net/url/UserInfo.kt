/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net.url

import aws.smithy.kotlin.runtime.text.encoding.Encodable
import aws.smithy.kotlin.runtime.text.encoding.PercentEncoding

/**
 * Represents the user authentication information in a URL
 * @param userName The username of the caller
 * @param password The password for the caller
 */
public class UserInfo private constructor(public val userName: Encodable, public val password: Encodable) {
    public companion object {
        /**
         * No username or password
         */
        public val Empty: UserInfo = UserInfo(Encodable.Empty, Encodable.Empty)

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

    override fun toString(): String = when {
        userName.isEmpty -> ""
        password.isEmpty -> userName.encoded
        else -> "${userName.encoded}:${userName.decoded}"
    }

    /**
     * A mutable builder used to construct [UserInfo] instances
     */
    public class Builder internal constructor(userInfo: UserInfo?) {
        /**
         * Initialize an empty [UserInfo] builder
         */
        public constructor() : this(null)

        private var userName = userInfo?.userName ?: Encodable.Empty

        /**
         * Gets or sets the username as a **decoded** string
         */
        public var decodedUserName: String
            get() = userName.decoded
            set(value) { userName = PercentEncoding.UserInfo.encodableFromDecoded(value) }

        /**
         * Gets or sets the username as an **encoded** string
         */
        public var encodedUserName: String
            get() = userName.encoded
            set(value) { userName = PercentEncoding.UserInfo.encodableFromDecoded(value) }

        private var password = userInfo?.password ?: Encodable.Empty

        /**
         * Gets or sets the password as a **decoded** string
         */
        public var decodedPassword: String
            get() = password.decoded
            set(value) { password = PercentEncoding.UserInfo.encodableFromDecoded(value) }

        /**
         * Gets or sets the password as an **encoded** string
         */
        public var encodedPassword: String
            get() = password.encoded
            set(value) { password = PercentEncoding.UserInfo.encodableFromEncoded(value) }

        internal fun parseDecoded(decoded: String) = parse(decoded, PercentEncoding.UserInfo::encodableFromDecoded)
        internal fun parseEncoded(encoded: String) = parse(encoded, PercentEncoding.UserInfo::encodableFromEncoded)

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
