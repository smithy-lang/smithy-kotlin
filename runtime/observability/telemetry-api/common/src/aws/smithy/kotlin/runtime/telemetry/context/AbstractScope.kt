/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.context

/**
 * An abstract implementation of a scope. By default, this class uses no-op implementations for all members unless
 * overridden in a subclass.
 */
public abstract class AbstractScope : Scope {
    override fun close() { }
}
