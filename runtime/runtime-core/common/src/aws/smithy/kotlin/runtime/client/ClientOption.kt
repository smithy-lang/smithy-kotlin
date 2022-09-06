/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.client

import aws.smithy.kotlin.runtime.util.AttributeKey
import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.InternalApi

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
    public operator fun contains(option: ClientOption<*>): Boolean

    /**
     * Creates or changes an [option] with the specified [value]
     */
    public fun <T : Any> set(option: ClientOption<T>, value: T)

    /**
     * Removes an option with the specified [option] if it exists
     */
    public fun <T : Any> remove(option: ClientOption<T>)
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
    override fun <T : Any> remove(option: ClientOption<T>): Unit = attributes.remove(option)
}
