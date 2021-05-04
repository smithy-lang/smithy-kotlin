/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.client

import software.aws.clientrt.util.AttributeKey
import software.aws.clientrt.util.Attributes
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Base class for building client options
 */
public abstract class ClientOptionsBuilder(protected val options: Attributes = Attributes()) : Attributes by options {
    private val requiredKeys = mutableSetOf<AttributeKey<*>>()

    // TODO - currently can only have nullable (T?) values delegated. Look at providing either a default/initial value
    // and/or a delegated property that can't be nullable and takes an initial value

    /**
     * Delegate a property to be set as an attribute using the given Key
     */
    protected fun <T : Any> option(key: ClientOption<T>): DelegatedClientOption<T> = DelegatedClientOption(key, options)

    /**
     * Like [option] but mark the key as required which is validated when [build] is called
     */
    protected fun <T : Any> requiredOption(key: AttributeKey<T>): DelegatedClientOption<T> {
        requiredKeys.add(key)
        return option(key)
    }

    /**
     * Build the options into an [ExecutionContext]
     */
    fun build(): ExecutionContext {
        val builder = this

        // verify the required properties were set
        requiredKeys.forEach { key ->
            if (!options.contains(key)) throw IllegalArgumentException("ClientOptionsBuilder: ${key.name} is a required property")
        }

        return ExecutionContext.build {
            // outright override the attributes since all of our property values
            // should be stored as attributes already
            this.attributes = builder.options
        }
    }
}

/**
 * Property delegate for a value meant to be stored as an attribute. Values are read and
 * written as an attribute into the Attributes instance [into] using the provided [key]
 */
public class DelegatedClientOption<T : Any>(
    private val key: AttributeKey<T>,
    private val into: Attributes
) : ReadWriteProperty<Any?, T?> {

    override fun getValue(thisRef: Any?, property: KProperty<*>): T? =
        into.getOrNull(key)

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        if (value == null) {
            into.remove(key)
        } else {
            into[key] = value
        }
    }
}
