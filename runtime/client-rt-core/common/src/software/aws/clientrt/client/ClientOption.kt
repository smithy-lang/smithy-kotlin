/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.client

import software.aws.clientrt.util.AttributeKey
import software.aws.clientrt.util.Attributes
import software.aws.clientrt.util.InternalApi

/**
 * A (service) client option that influences how the client behaves when executing requests
 */
public typealias ClientOption<T> = AttributeKey<T>

/**
 * Builder for configuring service client options manually
 */
public interface ClientOptions {
    /**
     * Check if the specified [option] exists
     */
    operator fun contains(option: ClientOption<*>): Boolean

    /**
     * Creates or changes an [option] with the specified [value]
     */
    fun <T : Any> set(option: ClientOption<T>, value: T)

    /**
     * Removes an option with the specified [option] if it exists
     */
    fun <T : Any> remove(option: ClientOption<T>)
}

/**
 * Wrapper around [Attributes] that provides a [ClientOptions] implementation
 */
@InternalApi
public class ClientOptionsImpl(private val attributes: Attributes) : ClientOptions {
    override fun <T : Any> set(option: ClientOption<T>, value: T) {
        attributes[option] = value
    }
    override fun contains(option: ClientOption<*>): Boolean = attributes.contains(option)
    override fun <T : Any> remove(option: ClientOption<T>) = attributes.remove(option)
}
