/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.otel

import aws.smithy.kotlin.runtime.util.AttributeKey
import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.get
import io.opentelemetry.api.common.AttributeKey as OtelAttributeKey
import io.opentelemetry.api.common.Attributes as OtelAttributes

@Suppress("UNCHECKED_CAST")
internal fun Attributes.toOtelAttributes(): OtelAttributes {
    val keys = this.keys
    if (keys.isEmpty()) return OtelAttributes.empty()
    val attrs = OtelAttributes.builder()
    keys.forEach {
        val key = it as AttributeKey<Any>
        val value = get(key)
        it.otelAttrKeyOrNull(value)?.let { otelKey ->
            attrs.put(otelKey, value)
        }
    }

    return attrs.build()
}

internal fun <T> AttributeKey<T>.otelAttrKeyOrNull(value: T): OtelAttributeKey<T>? {
    val otelKey = when (value) {
        is String -> OtelAttributeKey.stringKey(name)
        is Long -> OtelAttributeKey.longKey(name)
        is Boolean -> OtelAttributeKey.booleanKey(name)
        is Double -> OtelAttributeKey.doubleKey(name)
        is List<*> -> {
            when (value.firstOrNull()) {
                is String -> OtelAttributeKey.stringArrayKey(name)
                is Long -> OtelAttributeKey.longArrayKey(name)
                is Boolean -> OtelAttributeKey.booleanArrayKey(name)
                is Double -> OtelAttributeKey.doubleArrayKey(name)
                else -> null
            }
        }
        else -> null
    }

    @Suppress("UNCHECKED_CAST")
    return otelKey as? OtelAttributeKey<T>
}
