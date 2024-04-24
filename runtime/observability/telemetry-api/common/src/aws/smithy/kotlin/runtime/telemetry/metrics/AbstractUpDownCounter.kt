/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics

import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.telemetry.context.Context

/**
 * An abstract implementation of an up-down counter. By default, this class uses no-op implementations for all members
 * unless overridden in a subclass.
 */
public abstract class AbstractUpDownCounter : UpDownCounter {
    override fun add(value: Long, attributes: Attributes, context: Context?) { }
}
