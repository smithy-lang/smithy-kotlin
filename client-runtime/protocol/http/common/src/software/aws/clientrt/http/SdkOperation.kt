/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http

import software.aws.clientrt.http.feature.HttpDeserialize
import software.aws.clientrt.http.feature.HttpSerialize
import software.aws.clientrt.util.AttributeKey
import software.aws.clientrt.util.Attributes
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Configuration for an SDK (HTTP) operation/call
 */
open class SdkOperation {

    companion object {
        /**
         * The operation serializer (if any) is stored under this key
         */
        val OperationSerializer: AttributeKey<HttpSerialize> = AttributeKey("OperationSerializer")

        /**
         * The operation deserializer (if any) is stored under this key
         */
        val OperationDeserializer: AttributeKey<HttpDeserialize> = AttributeKey("OperationDeserializer")

        /**
         * The name of the operation is stored under this key
         */
        val OperationName: AttributeKey<String> = AttributeKey("OperationName")

        /**
         * The service name the operation belongs to is stored under this key
         */
        val ServiceName: AttributeKey<String> = AttributeKey("ServiceName")

        /**
         * The expected HTTP status code of a successful response is stored under this key
         */
        val ExpectedHttpStatus: AttributeKey<Int> = AttributeKey("ExpectedHttpStatus")

        /**
         * Build this operation into an HTTP [ExecutionContext]
         */
        fun build(block: Builder.() -> Unit): ExecutionContext = Builder().apply(block).build()
    }

    /**
     * Convenience builder for constructing HTTP client operations
     */
    open class Builder {

        /**
         * Additional per/operation attributes
         */
        private var attributes: Attributes = Attributes()

        private val requiredKeys = mutableSetOf<AttributeKey<*>>()

        /**
         * Delegate a property to be set as an attribute using the given Key
         */
        protected fun <T : Any> attribute(key: AttributeKey<T>): ExecutionAttribute<T> {
            return ExecutionAttribute(key, attributes)
        }

        /**
         * Like [attribute] but mark the key as required which is validated when [build] is called
         */
        protected fun <T : Any> requiredAttribute(key: AttributeKey<T>): ExecutionAttribute<T> {
            requiredKeys.add(key)
            return attribute(key)
        }

        /**
         * The service name
         */
        var service: String? by requiredAttribute(ServiceName)

        /**
         * The name of the operation
         */
        var operationName: String? by requiredAttribute(OperationName)

        /**
         * The serializer to use for the request
         */
        var serializer: HttpSerialize? by attribute(OperationSerializer)

        /**
         * The deserializer to use for the response
         */
        var deserializer: HttpDeserialize? by attribute(OperationDeserializer)

        /**
         * The expected HTTP status code on success
         */
        var expectedHttpStatus: Int? by attribute(ExpectedHttpStatus)

        /**
         * Set request specific attributes for this execution
         */
        fun setAttributes(block: Attributes.() -> Unit) = attributes.apply(block)

        fun build(): ExecutionContext {
            val builder = this

            // verify the required properties were set
            requiredKeys.forEach { key ->
                if (!attributes.contains(key)) throw IllegalArgumentException("SdkOperation: ${key.name} is a required property")
            }

            return ExecutionContext.build {
                // outright override the attributes since all of our property values
                // should be stored as attributes already
                this.attributes = builder.attributes
            }
        }
    }
}

/**
 * Property delegate for a value meant to be stored as an attribute. Values are read and
 * written as an attribute into the Attributes instance [into] using the provided [key]
 */
class ExecutionAttribute<T : Any>(
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
